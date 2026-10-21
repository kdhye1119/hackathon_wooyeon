"""
우연한식탁_가상데이터.xlsx (신청 폼 응답) → 실행 중인 백엔드에 참가자로 넣는다.

참가자 앱과 같은 흐름으로 넣는다: 로그인(휴대폰) → 프로필 → 동의 → 신청 → 모의 결제.
  python backend/scripts/import_virtual_data.py [엑셀 경로] [--base http://localhost:8080] [--limit N] [--capacity 48]

데이터 그대로 넣을 수 없는 부분:
- 연락처가 비어 있어 0107로 시작하는 가짜 번호를 행 순서대로 만든다 (다시 실행하면 같은 사람으로 로그인)
- 직업·인스타그램이 비어 있으면 '미입력'
- 응답 날짜(5~11월 목요일)는 이미 지났으므로, 같은 날짜를 고른 사람끼리 모이도록
  모집 중인 목요일 회차(가까운 3개)에 날짜별로 나눠 옮긴다
- 3·4회권(이전 상품)은 FAQ 기준 상품인 멤버십 1개월로 넣는다
- 결제는 약 90% 완료, 일부는 결제 대기·실패로 남긴다 (화면에서 상태별로 보이도록)
"""
import argparse
import json
import random
import re
import sys
import urllib.error
import urllib.request
from datetime import date

import openpyxl

INTEREST = {'영화·드라마': 'MOVIE_DRAMA', '음악': 'MUSIC', '독서·글쓰기': 'READING_WRITING', '운동 (헬스, 러닝, 클라이밍 등)': 'WORKOUT', '맛집·카페 탐방': 'FOOD_CAFE', '여행': 'TRAVEL', '사진·영상': 'PHOTO_VIDEO', '전시·공연·아트': 'EXHIBITION_ART', '패션·뷰티': 'FASHION_BEAUTY', '자기계발·공부': 'SELF_DEVELOPMENT'}
TRAIT = {'잔잔하고 조용한 편': 'CALM', '활기차고 에너지 있는 편': 'ENERGETIC', '깊은 대화를 좋아함': 'DEEP_TALK', '가볍고 유머 있는 대화를 좋아함': 'HUMOR', '경청을 잘 함': 'LISTENER', '이야기를 이끄는 편': 'LEADER'}
DESIRED = {'나와 비슷한 사람': 'SIMILAR', '나와 다른 자극을 주는 사람': 'DIFFERENT', '깊은 대화가 되는 사람': 'DEEP_TALK', '편하고 유머 있는 사람': 'HUMOR', '열정 있는 사람': 'PASSIONATE', '잔잔하고 안정적인 사람': 'CALM_STABLE'}
PRICE = {'10,000~20,000원': 'P10_20', '20,000~30,000원': 'P20_30', '30,000원 이상 (상관 없음)': 'P30_PLUS'}
REGION = {'홍대/합정': 'HONGDAE_HAPJEONG', '잠실': 'JAMSIL', '강남/신논현': 'GANGNAM', '강남': 'GANGNAM', '신논현': 'GANGNAM', '을지로/종로': 'EULJIRO_JONGNO'}
NO_FOOD = re.compile(r'^(없|아니|x$|-$|no|n$|딱히|전혀|가리는)', re.I)


def pick(cell, table):
    """복수 선택 셀 → 코드 목록. '운동 (헬스, 러닝, 클라이밍 등)'처럼 쉼표가 든 선택지가 있어 긴 라벨부터 찾는다."""
    text = str(cell or '')
    out = []
    for label in sorted(table, key=len, reverse=True):
        if label in text:
            out.append(table[label])
            text = text.replace(label, '')
    return list(dict.fromkeys(out))


class Api:
    def __init__(self, base):
        self.base = base

    def call(self, method, path, token=None, body=None):
        req = urllib.request.Request(self.base + path, method=method, data=None if body is None else json.dumps(body).encode('utf-8'), headers={'Content-Type': 'application/json; charset=utf-8', **({'Authorization': f'Bearer {token}'} if token else {})})
        try:
            with urllib.request.urlopen(req) as r:
                text = r.read().decode('utf-8')
                return r.status, json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode('utf-8', 'replace')[:300]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('xlsx', nargs='?', default='우연한식탁_가상데이터.xlsx')
    ap.add_argument('--base', default='http://localhost:8080')
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--admin', default='admin:admin1234')
    ap.add_argument('--capacity', type=int, default=0, help='대상 회차 정원을 이 값으로 늘린다 (기본 24명이면 응답자 일부가 정원 마감으로 빠짐)')
    args = ap.parse_args()
    api = Api(args.base)

    login_id, pw = args.admin.split(':', 1)
    s, r = api.call('POST', '/api/auth/admin/login', body={'loginId': login_id, 'password': pw})
    if s != 200:
        sys.exit(f'운영자 로그인 실패: {s} {r}')
    admin = r['token']

    # 모집 중인 목요일 회차 날짜 (가까운 3개)
    s, slots = api.call('GET', '/api/admin/slots', admin)
    targets = sorted({x['date'] for x in slots if x['status'] == 'RECRUITING' and date.fromisoformat(x['date']).weekday() == 3})[:3]
    if not targets:
        sys.exit('모집 중인 목요일 회차가 없어요. 회차 관리에서 먼저 만들어 주세요.')
    print('신청 날짜를 옮길 회차:', targets)
    if args.capacity:
        for x in slots:
            if x['status'] == 'RECRUITING' and x['date'] in targets and x['capacity'] < args.capacity:
                api.call('PUT', f"/api/admin/slots/{x['id']}", admin, {'capacity': args.capacity})
        print(f'대상 회차 정원 → {args.capacity}명')

    rows = [r for r in list(openpyxl.load_workbook(args.xlsx, read_only=True, data_only=True).worksheets[0].iter_rows(values_only=True))[2:] if r[1]]
    # 날짜 대응은 전체 응답 기준으로 정해 --limit 여부와 상관없이 같게
    form_dates = sorted({d for r in rows for d in re.findall(r'(\d{1,2})/(\d{1,2})', str(r[13] or ''))}, key=lambda md: (int(md[0]), int(md[1])))
    date_map = {md: targets[i % len(targets)] for i, md in enumerate(form_dates)}
    if args.limit:
        rows = rows[: args.limit]

    rnd = random.Random(42)
    stats = {'ok': 0, 'paid': 0, 'pending': 0, 'failed': 0, 'skipped': []}
    for i, r in enumerate(rows):
        name = str(r[1]).strip()
        phone = f'0107{i + 1:07d}'
        mbti_m = re.search(r'[EI][SN][TF][JP]', str(r[7] or '').upper())
        regions = pick(r[8], REGION)
        other = [x.strip() for x in str(r[8] or '').split(',') if x.strip() and x.strip() not in REGION]
        dates = sorted({date_map[md] for md in re.findall(r'(\d{1,2})/(\d{1,2})', str(r[13] or '')) if md in date_map})
        # 응답의 3·4회권(이전 상품)은 FAQ 기준 상품인 멤버십 1개월로 넣는다
        product = 'MEMBERSHIP_1M' if re.search(r'[34]회권', str(r[14] or '')) else 'SINGLE'
        food = str(r[16] or '').strip()
        profile = {
            'name': name,
            'birthYear': 2026 - int(r[4]) if r[4] else 1996,
            'gender': 'FEMALE' if str(r[5]).startswith('여') else 'MALE',
            'job': str(r[6]).strip() if r[6] else '미입력',
            'instagramId': str(r[3]).strip() if r[3] else '미입력',
            'mbti': mbti_m.group(0) if mbti_m else 'INFP',
            'interests': pick(r[9], INTEREST) or ['FOOD_CAFE'],
            'traits': pick(r[10], TRAIT) or ['LISTENER'],
            'desiredTypes': pick(r[11], DESIRED) or ['HUMOR'],
            'priceRanges': pick(r[12], PRICE) or ['P20_30'],
            'foodRestrictions': None if not food or NO_FOOD.search(food) else food,
        }
        s, login = api.call('POST', '/api/auth/login', body={'phone': phone, 'name': name})
        if s != 200:
            stats['skipped'].append((name, '로그인', login))
            continue
        tok = login['token']
        s, res = api.call('PUT', '/api/me/profile', tok, profile)
        if s != 200:
            stats['skipped'].append((name, '프로필', res))
            continue
        api.call('POST', '/api/me/consents', tok, {'privacy': True, 'policy': True})
        if not regions or not dates:
            stats['skipped'].append((name, '신청', f"서울 4개 지역·날짜 없음 (기타 지역: {', '.join(other) or '-'})"))
            continue
        s, sub = api.call('POST', '/api/applications', tok, {'dates': dates, 'regions': regions, 'otherRegion': ', '.join(other) or None, 'product': product, 'ticketId': None})
        if s != 200:
            stats['skipped'].append((name, '신청', sub))
            continue
        stats['ok'] += 1
        if sub.get('paymentRequired'):
            roll = rnd.random()
            if roll < 0.9:
                api.call('POST', f"/api/payments/{sub['paymentId']}/confirm", tok, {'simulateFailure': False})
                stats['paid'] += 1
            elif roll < 0.95:
                api.call('POST', f"/api/payments/{sub['paymentId']}/confirm", tok, {'simulateFailure': True})
                stats['failed'] += 1
            else:
                stats['pending'] += 1
        if (i + 1) % 50 == 0:
            print(f'{i + 1}/{len(rows)}')

    print(f"신청 {stats['ok']}명 (결제 완료 {stats['paid']}, 결제 실패 {stats['failed']}, 결제 대기 {stats['pending']})")
    print(f"신청하지 못함 {len(stats['skipped'])}명")
    for n, step, why in stats['skipped']:
        print(' -', n, step, str(why)[:120])


if __name__ == '__main__':
    main()
