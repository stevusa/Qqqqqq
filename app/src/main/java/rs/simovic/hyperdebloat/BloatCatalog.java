package rs.simovic.hyperdebloat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class BloatCatalog {

    public enum Level {
        SAFE_AUTO,
        OPTIONAL
    }

    public static final class Item {
        public final String packageName;
        public final String title;
        public final String reason;
        public final Level level;

        public Item(String packageName, String title, String reason, Level level) {
            this.packageName = packageName;
            this.title = title;
            this.reason = reason;
            this.level = level;
        }
    }

    // "SAFE_AUTO" je namerno vrlo kratak: samo reklame/telemetrija i OEM pomoćni stubovi.
    // Korisničke aplikacije poput Browser/Music/Weather nisu automatski uklonjene.
    public static final List<Item> ITEMS = Arrays.asList(
        new Item("com.miui.msa.global", "MIUI/HyperOS MSA", "Sistemski reklamni servis.", Level.SAFE_AUTO),
        new Item("com.miui.analytics", "MIUI Analytics", "OEM analitika i telemetrija.", Level.SAFE_AUTO),
        new Item("com.xiaomi.mipicks", "GetApps", "Xiaomi prodavnica/preporuke aplikacija.", Level.SAFE_AUTO),
        new Item("com.mi.globalbrowser", "Mi Browser", "Opcioni Xiaomi pregledač.", Level.OPTIONAL),
        new Item("com.miui.player", "Mi Music", "Opcioni Xiaomi muzički plejer.", Level.OPTIONAL),
        new Item("com.miui.videoplayer", "Mi Video", "Opcioni Xiaomi video plejer.", Level.OPTIONAL),
        new Item("com.miui.weather2", "Weather", "Opciona Xiaomi vremenska prognoza.", Level.OPTIONAL),
        new Item("com.miui.yellowpage", "Yellow Pages", "Opcioni servis za identifikaciju/poslovne brojeve.", Level.OPTIONAL),
        new Item("com.facebook.appmanager", "Facebook App Manager", "OEM Facebook pomoćni servis, ako postoji.", Level.SAFE_AUTO),
        new Item("com.facebook.services", "Facebook Services", "OEM Facebook pomoćni servis, ako postoji.", Level.SAFE_AUTO),
        new Item("com.facebook.system", "Facebook System", "OEM Facebook pomoćni servis, ako postoji.", Level.SAFE_AUTO)
    );

    private static final Set<String> HARD_BLOCK = new HashSet<>(Arrays.asList(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.android.launcher3",
        "com.google.android.inputmethod.latin",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.xiaomi.finddevice",
        "com.android.providers.telephony",
        "com.android.providers.contacts",
        "com.android.providers.media",
        "com.android.documentsui",
        "com.android.externalstorage"
    ));

    private BloatCatalog() {}

    public static boolean isHardBlocked(String pkg) {
        if (pkg == null) return true;
        if (HARD_BLOCK.contains(pkg)) return true;
        String p = pkg.toLowerCase();
        return p.startsWith("com.android.systemui")
                || p.contains("permissioncontroller")
                || p.contains("packageinstaller")
                || p.endsWith(".launcher")
                || p.contains("telephonyprovider");
    }

    public static Map<String, Item> map() {
        Map<String, Item> map = new LinkedHashMap<>();
        for (Item i : ITEMS) map.put(i.packageName, i);
        return map;
    }

    public static List<Item> presentIn(Set<String> installed) {
        List<Item> out = new ArrayList<>();
        for (Item i : ITEMS) {
            if (installed.contains(i.packageName)) out.add(i);
        }
        return out;
    }
}
