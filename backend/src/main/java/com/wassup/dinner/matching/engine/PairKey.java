package com.wassup.dinner.matching.engine;

/** 순서 없는 사용자 쌍 (a < b). */
public record PairKey(long a, long b) {

    public static PairKey of(long x, long y) {
        return x < y ? new PairKey(x, y) : new PairKey(y, x);
    }
}
