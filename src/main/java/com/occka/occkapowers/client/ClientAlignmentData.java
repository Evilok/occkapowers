package com.occka.occkapowers.client;

public class ClientAlignmentData {
    public static String alignment = "";

    public static void set(String value) {
        alignment = value == null ? "" : value;
    }

    public static boolean isHero()    { return "hero".equals(alignment); }
    public static boolean isVillain() { return "villain".equals(alignment); }
}