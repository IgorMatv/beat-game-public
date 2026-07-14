package com.beatgame.track;

public enum Decade {
    D1980(1980, 1989),
    D1990(1990, 1999),
    D2000(2000, 2009),
    D2010(2010, 2019),
    D2020(2020, 2029);

    public final int from;
    public final int to;

    Decade(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public static Decade fromYear(int year) {
        for (Decade d : values()) {
            if (year >= d.from && year <= d.to) return d;
        }
        return null;
    }
}
