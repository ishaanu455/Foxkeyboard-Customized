package unicode.sinhala.keyboard

enum class Function { ACTION, SHIFT, LANG, IME, BACKSPACE, PANEL }
enum class KeyboardLayout { ENGLISH, WIJESEKARA, SINGLISH }
enum class CharType { SWARA, WYANJANA, PILI, LAKUNU, UNKNOWN }

/**
 * The visual style used to render emoji in the keyboard.
 *
 * SYSTEM  - uses whatever emoji font the phone manufacturer ships (default, always works offline).
 * TWEMOJI - downloads and shows the open-source Twemoji artwork (CC-BY 4.0), which has a flat,
 *           colorful look similar in spirit to Apple's style. This is NOT Apple's actual emoji
 *           font/artwork - that is proprietary and can't legally be bundled or redistributed.
 * CUSTOM  - renders emoji using a .ttf/.otf font file the user picks from their own device
 *           storage. The app never bundles or distributes this file; it only reads the one
 *           local copy the user explicitly selected via the system file picker.
 */
enum class EmojiStyle(val id: String, val displayName: String) {
    SYSTEM("system", "Mobile (System Default)"),
    TWEMOJI("twemoji", "iOS-style (Twemoji, open-source)"),
    CUSTOM("custom", "Custom (Your Font File)");

    companion object {
        fun fromId(id: String?): EmojiStyle = entries.find { it.id == id } ?: SYSTEM
    }
}
enum class CHAR(val code: Int, val text: String) {
    /** අ | 3461 */
    AYANNA(3461, "අ"),

    /** ආ | 3462 */
    AAYANNA(3462, "ආ"),

    /** ඇ | 3463 */
    AEYANNA(3463, "ඇ"),

    /** ඈ | 3464 */
    AEEYANNA(3464, "ඈ"),

    /** ඉ | 3465 */
    IYANNA(3465, "ඉ"),

    /** ඊ | 3466 */
    IIYANNA(3466, "ඊ"),

    /** උ | 3467 */
    UYANNA(3467, "උ"),

    /** ඌ | 3468 */
    UUYANNA(3468, "ඌ"),

    /** ඍ | 3469 */
    IRUYANNA(3469, "ඍ"),

    /** ඎ | 3470 */
    IRUUYANNA(3470, "ඎ"),

    /** ඏ | 3471 */
    ILUYANNA(3471, "ඏ"),

    /** ඐ | 3472 */
    ILUUYANNA(3472, "ඐ"),

    /** එ | 3473 */
    EYANNA(3473, "එ"),

    /** ඒ | 3474 */
    EEYANNA(3474, "ඒ"),

    /** ඓ | 3475 */
    AIYANNA(3475, "ඓ"),

    /** ඔ | 3476 */
    OYANNA(3476, "ඔ"),

    /** ඕ | 3477 */
    OOYANNA(3477, "ඕ"),

    /** ඖ | 3478 */
    AUYANNA(3478, "ඖ"),

    /** ක | 3482 */
    ALPAPRAANA_KAYANNA(3482, "ක"),

    /** ඛ | 3483 */
    MAHAAPRAANA_KAYANNA(3483, "ඛ"),

    /** ග | 3484 */
    ALPAPRAANA_GAYANNA(3484, "ග"),

    /** ඝ | 3485 */
    MAHAAPRAANA_GAYANNA(3485, "ඝ"),

    /** ඞ | 3486 */
    KANTAJA_NAASIKYAYA(3486, "ඞ"),

    /** ඟ | 3487 */
    SANYAKA_GAYANNA(3487, "ඟ"),

    /** ච | 3488 */
    ALPAPRAANA_CAYANNA(3488, "ච"),

    /** ඡ | 3489 */
    MAHAAPRAANA_CAYANNA(3489, "ඡ"),

    /** ජ | 3490 */
    ALPAPRAANA_JAYANNA(3490, "ජ"),

    /** ඣ | 3491 */
    MAHAAPRAANA_JAYANNA(3491, "ඣ"),

    /** ඤ | 3492 */
    TAALUJA_NAASIKYAYA(3492, "ඤ"),

    /** ඥ | 3493 */
    TAALUJA_SANYOOGA_NAAKSIKYAYA(3493, "ඥ"),

    /** ඦ | 3494 */
    SANYAKA_JAYANNA(3494, "ඦ"),

    /** ට | 3495 */
    ALPAPRAANA_TTAYANNA(3495, "ට"),

    /** ඨ | 3496 */
    MAHAAPRAANA_TTAYANNA(3496, "ඨ"),

    /** ඩ | 3497 */
    ALPAPRAANA_DDAYANNA(3497, "ඩ"),

    /** ඪ | 3498 */
    MAHAAPRAANA_DDAYANNA(3498, "ඪ"),

    /** ණ | 3499 */
    MUURDHAJA_NAYANNA(3499, "ණ"),

    /** ඬ | 3500 */
    SANYAKA_DDAYANNA(3500, "ඬ"),

    /** ත | 3501 */
    ALPAPRAANA_TAYANNA(3501, "ත"),

    /** ථ | 3502 */
    MAHAAPRAANA_TAYANNA(3502, "ථ"),

    /** ද | 3503 */
    ALPAPRAANA_DAYANNA(3503, "ද"),

    /** ධ | 3504 */
    MAHAAPRAANA_DAYANNA(3504, "ධ"),

    /** න | 3505 */
    DANTAJA_NAYANNA(3505, "න"),

    /** ඳ | 3507 */
    SANYAKA_DAYANNA(3507, "ඳ"),

    /** ප | 3508 */
    ALPAPRAANA_PAYANNA(3508, "ප"),

    /** ඵ | 3509 */
    MAHAAPRAANA_PAYANNA(3509, "ඵ"),

    /** බ | 3510 */
    ALPAPRAANA_BAYANNA(3510, "බ"),

    /** භ | 3511 */
    MAHAAPRAANA_BAYANNA(3511, "භ"),

    /** ම | 3512 */
    MAYANNA(3512, "ම"),

    /** ඹ | 3513 */
    AMBA_BAYANNA(3513, "ඹ"),

    /** ය | 3514 */
    YAYANNA(3514, "ය"),

    /** ර | 3515 */
    RAYANNA(3515, "ර"),

    /** ල | 3517 */
    DANTAJA_LAYANNA(3517, "ල"),

    /** ව | 3520 */
    VAYANNA(3520, "ව"),

    /** ශ | 3521 */
    TAALUJA_SAYANNA(3521, "ශ"),

    /** ෂ | 3522 */
    MUURDHAJA_SAYANNA(3522, "ෂ"),

    /** ස | 3523 */
    DANTAJA_SAYANNA(3523, "ස"),

    /** හ | 3524 */
    HAYANNA(3524, "හ"),

    /** ළ | 3525 */
    MUURDHAJA_LAYANNA(3525, "ළ"),

    /** ෆ | 3526 */
    FAYANNA(3526, "ෆ"),

    /** ා | 3535 */
    AELA_PILLA(3535, "ා"),

    /** ැ | 3536 */
    KETTI_AEDA_PILLA(3536, "ැ"),

    /** ෑ | 3537 */
    DIGA_AEDA_PILLA(3537, "ෑ"),

    /** ි | 3538 */
    KETTI_IS_PILLA(3538, "ි"),

    /** ී | 3539 */
    DIGA_IS_PILLA(3539, "ී"),

    /** ු | 3540 */
    KETTI_PAA_PILLA(3540, "ු"),

    /** ූ | 3542 */
    DIGA_PAA_PILLA(3542, "ූ"),

    /** ෘ | 3544 */
    GAETTA_PILLA(3544, "ෘ"),

    /** ෙ | 3545 */
    KOMBUVA(3545, "ෙ"),

    /** ේ | 3546 */
    DIGA_KOMBUVA(3546, "ේ"),

    /** ෛ | 3547 */
    KOMBU_DEKA(3547, "ෛ"),

    /** ො | 3548 */
    KOMBUVA_HAA_AELA_PILLA(3548, "ො"),

    /** ෝ | 3549 */
    KOMBUVA_HAA_DIGA_AELA_PILLA(3549, "ෝ"),

    /** ෞ | 3550 */
    KOMBUVA_HAA_GAYANUKITTA(3550, "ෞ"),

    /** ෟ | 3551 */
    GAYANUKITTA(3551, "ෟ"),

    /** ෳ | 3571 */
    DIGA_GAYANUKITTA(3571, "ෳ"),

    /** ෲ | 3570 */
    DIGA_GAETTA_PILLA(3570, "ෲ"),

    /** ං | 3458 */
    SIGN_ANUSVARAYA(3458, "ං"),

    /** ඃ | 3459 */
    SIGN_VISARGAYA(3459, "ඃ"),

    /** ් | 3530 */
    SIGN_AL_LAKUNA(3530, "්"),

    /** Zero Width Joiner | 8205 */
    ZERO_WIDTH_JOINER(8205, "\u200D"),

    /** Full Stop */
    COMMA(44, ","),

    /** Full Stop */
    FULL_STOP(46, "."),

    /** LESS-THAN SIGN */
    LESS_THAN(60, "<"),

    /** GREATER-THAN SIGN */
    GREATER_THAN(62, ">"),

    /** COLON */
    COLON(58, ":"),

    /** SEMICOLON */
    SEMICOLON(59, ";"),

    /** යංශය (් + ZWJ + ය) | 3530 + 8205 + 3514 */
    SIGN_YANSHAYA(-1, "්\u200Dය"),

    /** රකාරාංශය (් + ZWJ + ර) | 3530 + 8205 + 3515 */
    SIGN_RAKARANSHAYA(-2, "්\u200Dර"),

    /** රේපය (ර + ් + ZWJ) | 3515 + 3530 + 8205 */
    SIGN_REEPAYA(-3, "ර්\u200D"),


    MARK_SANYAKA(-8, ""),

    EMPTY(-9, "");

    val type get() = getCharType(code)
}

enum class SYMBOL(val code: Int, val text: String) {
    /**  ` | 96 */
    GRAVE_ACCENT(96, "`"),

    /**  ~ | 126 */
    TILDE(126, "~"),

    /**  ! | 33 */
    EXCLAMATION_MARK(33, "!"),

    /**  @ | 64 */
    COMMERCIAL_AT(64, "@"),

    /**  # | 35 */
    NUMBER_SIGN(35, "#"),

    /**  $  36 */
    DOLLAR_SIGN(36, "$"),

    /**  % | 36 */
    PERCENT_SIGN(36, "%"),

    /**  ^ | 94 */
    CIRCUMFLEX_ACCENT(94, "^"),

    /**  & | 38 */
    AMPERSAND(38, "&"),

    /**  * | 42 */
    ASTERISK(42, "*"),

    /** ( | 40 */
    LEFT_PARENTHESIS(40, "("),

    /** ) | 41 */
    RIGHT_PARENTHESIS(41, ")"),

    /** - | 45 */
    HYPHEN_MINUS(45, "-"),

    /** _  95 */
    LOW_LINE(95, "_"),

    /** = | 61 */
    EQUALS_SIGN(61, "="),

    /** + | 43 */
    PLUS_SIGN(43, "+"),

    /** [ | 91 */
    LEFT_SQUARE_BRACKET(91, "["),

    /** ] | 93 */
    RIGHT_SQUARE_BRACKET(93, "]"),

    /** { | 123 */
    LEFT_CURLY_BRACKET(123, "{"),

    /** } | 125 */
    RIGHT_CURLY_BRACKET(125, "}"),

    /** \ | 92 */
    REVERSE_SOLIDUS(92, "\\"),

    /** | | 124 */
    VERTICAL_LINE(124, "|"),

    /** ; | 59 */
    SEMICOLON(59, ";"),

    /** : | 58 */
    COLON(58, ":"),

    /** ' | 39 */
    APOSTROPHE(39, "'"),

    /** " | 34 */
    QUOTATION_MARK(34, "\""),

    /** , | 44 */
    COMMA(44, ","),

    /** . | 46 */
    FULL_STOP(46, "."),

    /** < | 60 */
    LESS_THAN_SIGN(60, "<"),

    /** > | 62 */
    GREATER_THAN_SIGN(62, ">"),

    /** / | 47 */
    SOLIDUS(47, "/"),

    /** ? | 63 */
    QUESTION_MARK(63, "?"),

    /** ÷ | 247 */
    DIVISION_SIGN(247, "÷"),

    /** × | 215 */
    MULTIPLICATION_SIGN(215, "×"),

    /** ± | 177 */
    PLUS_MINUS_SIGN(177, "±"),

    /** • | 8226 */
    BULLET(8226, "•"),

    /** ◦ | 9702 */
    WHITE_BULLET(9702, "◦"),

    /** ▪ | 9642 */
    BLACK_SMALL_SQUARE(9642, "▪"),

    /** ▫ | 9643 */
    WHITE_SMALL_SQUARE(9643, "▫"),

    /** ‣ | 8227 */
    TRIANGULAR_BULLET(8227, "‣"),

    /** √ | 8730 */
    SQUARE_ROOT(8730, "√"),

    /** π | 960 */
    GREEK_SMALL_LETTER_PI(960, "π"),

    /** ¶ | 182 */
    PILCROW_SIGN(182, "¶"),

    /** ∆ | 8710 */
    INCREMENT(8710, "∆"),

    /** € | 8364 */
    EURO_SIGN(8364, "€"),

    /** ¥ | 165 */
    YEN_SIGN(165, "¥"),

    /** £ | 163 */
    POUND_SIGN(163, "£"),

    /** ¢ | 162 */
    CENT_SIGN(162, "¢"),

    /** ° | 176 */
    DEGREE_SIGN(176, "°"),

    /** © | 169 */
    COPYRIGHT_SIGN(169, "©"),

    /** ® | 174 */
    REGISTERED_SIGN(174, "®"),

    /** ™ | 8482 */
    TRADE_MARK_SIGN(8482, "™"),

    /** ℅ | 8453 */
    CARE_OF(8453, "℅"),

    /** 🄍 | 127245 */
    CIRCLED_ZERO_WITH_SLASH(127245, "϶"),

    /** 🄎 | 127246 */
    CIRCLED_ANTICLOCKWISE_ARROW(127246, "ᐳ"),

    /** 🄏 | 127247 */
    CIRCLED_DOLLAR_SIGN_WITH_OVERLAID_BACKSLASH(127247, "ᐸ"),

    /** 🅭 | 127341 */
    CIRCLED_CC(127341, "ᾪ"),

    /** 🅮 | 127342 */
    CIRCLED_C_WITH_OVERLAID_BACKSLASH(127342, "⁘"),

    /** 🅯 | 127343 */
    CIRCLED_HUMAN_FIGURE(127342, "∗"),

    /** ⊜ | 8860 */
    CIRCLED_EQUALS(8860, "⊜"),


}
