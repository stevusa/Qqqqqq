package rs.simovic.hyperdebloat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQ_SHIZUKU = 7001;
    private static final Pattern PKG = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");

    private TextView status;
    private LinearLayout candidatesBox;
    private Button scanBtn, safeCleanBtn, selectedCleanBtn, restoreBtn;

    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private final List<String> removedThisSession = new ArrayList<>();

    private IDebloatService service;
    private Shizuku.UserServiceArgs userServiceArgs;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQ_SHIZUKU) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        bindPrivilegedService();
                    } else {
                        setStatus("Shizuku dozvola nije odobrena.");
                    }
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::ensurePermissionAndBind;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        service = null;
        runOnUiThread(() -> {
            setStatus("Shizuku nije aktivan. Pokreni Shizuku pa pokušaj ponovo.");
            updateButtons();
        });
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IDebloatService.Stub.asInterface(binder);
            runOnUiThread(() -> {
                setStatus("Shizuku povezan. Možeš skenirati telefon.");
                updateButtons();
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            runOnUiThread(() -> {
                setStatus("Privilegovani servis je prekinut.");
                updateButtons();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);

        try {
            if (Shizuku.pingBinder()) ensurePermissionAndBind();
            else setStatus("Pokreni Shizuku preko Wireless debugging-a.");
        } catch (Throwable t) {
            setStatus("Shizuku nije dostupan: " + t.getMessage());
        }
        updateButtons();
    }

    private void buildUi() {
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Hyper Debloat");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Bezbedniji debloat za HyperOS/Android preko Shizuku. Sistem-kritični paketi su blokirani.");
        sub.setTextSize(15);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status);

        Button connectBtn = new Button(this);
        connectBtn.setText("Poveži Shizuku");
        connectBtn.setOnClickListener(v -> ensurePermissionAndBind());
        root.addView(connectBtn);

        scanBtn = new Button(this);
        scanBtn.setText("Skeniraj nepotrebne aplikacije");
        scanBtn.setOnClickListener(v -> scan());
        root.addView(scanBtn);

        safeCleanBtn = new Button(this);
        safeCleanBtn.setText("Bezbedno automatsko čišćenje");
        safeCleanBtn.setOnClickListener(v -> confirmSafeClean());
        root.addView(safeCleanBtn);

        selectedCleanBtn = new Button(this);
        selectedCleanBtn.setText("Ukloni označene");
        selectedCleanBtn.setOnClickListener(v -> confirmSelected());
        root.addView(selectedCleanBtn);

        restoreBtn = new Button(this);
        restoreBtn.setText("Vrati uklonjene iz ove sesije");
        restoreBtn.setOnClickListener(v -> restoreSession());
        root.addView(restoreBtn);

        TextView heading = new TextView(this);
        heading.setText("Pronađeni kandidati");
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(18), 0, dp(8));
        root.addView(heading);

        candidatesBox = new LinearLayout(this);
        candidatesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(candidatesBox);

        TextView note = new TextView(this);
        note.setText(
            "\nNapomena: „ukloni“ koristi pm uninstall --user 0. " +
            "APK ostaje u sistemskoj particiji i paket se može vratiti sa install-existing. " +
            "Ovo ne briše podatke drugih aplikacija i ne radi factory reset."
        );
        note.setTextSize(13);
        root.addView(note);

        setContentView(scroll);
    }

    private void ensurePermissionAndBind() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku nije pokrenut.");
                return;
            }
            if (Shizuku.isPreV11()) {
                setStatus("Potrebna je novija Shizuku API verzija.");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindPrivilegedService();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                setStatus("Shizuku dozvola je odbijena. Otvori Shizuku i dozvoli Hyper Debloat.");
            } else {
                Shizuku.requestPermission(REQ_SHIZUKU);
            }
        } catch (Throwable t) {
            setStatus("Greška Shizuku: " + t.getMessage());
        }
    }

    private void bindPrivilegedService() {
        try {
            if (service != null) return;
            userServiceArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(this, DebloatService.class))
                    .daemon(false)
                    .processNameSuffix("debloat")
                    .debuggable(BuildConfig.DEBUG)
                    .version(1)
                    .tag("hyper-debloat-service");

            Shizuku.bindUserService(userServiceArgs, connection);
            setStatus("Povezujem privilegovani servis...");
        } catch (Throwable t) {
            setStatus("Ne mogu da povežem servis: " + t.getMessage());
        }
    }

    private void scan() {
        if (service == null) {
            ensurePermissionAndBind();
            toast("Prvo poveži Shizuku.");
            return;
        }
        setStatus("Skeniram...");
        setWorking(true);

        new Thread(() -> {
            try {
                String raw = service.exec("pm list packages --user 0");
                Set<String> installed = parsePackages(raw);
                List<BloatCatalog.Item> found = BloatCatalog.presentIn(installed);

                runOnUiThread(() -> showCandidates(found));
            } catch (Throwable t) {
                runOnUiThread(() -> setStatus("Skeniranje nije uspelo: " + t.getMessage()));
            } finally {
                runOnUiThread(() -> setWorking(false));
            }
        }).start();
    }

    private Set<String> parsePackages(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith("package:")) {
                String p = line.substring(8).trim();
                if (PKG.matcher(p).matches()) out.add(p);
            }
        }
        return out;
    }

    private void showCandidates(List<BloatCatalog.Item> found) {
        candidatesBox.removeAllViews();
        checks.clear();

        if (found.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nisam našao nijedan paket iz bezbednog kataloga.");
            candidatesBox.addView(empty);
            setStatus("Skeniranje završeno — nema poznatih kandidata.");
            updateButtons();
            return;
        }

        int autoCount = 0;
        for (BloatCatalog.Item item : found) {
            if (BloatCatalog.isHardBlocked(item.packageName)) continue;

            CheckBox cb = new CheckBox(this);
            String level = item.level == BloatCatalog.Level.SAFE_AUTO ? "AUTO" : "OPCIONO";
            cb.setText(item.title + "  [" + level + "]\n" + item.packageName + "\n" + item.reason);
            cb.setPadding(0, dp(6), 0, dp(6));
            cb.setChecked(item.level == BloatCatalog.Level.SAFE_AUTO);
            candidatesBox.addView(cb);
            checks.put(item.packageName, cb);

            if (item.level == BloatCatalog.Level.SAFE_AUTO) autoCount++;
        }

        setStatus("Nađeno: " + checks.size() + " kandidata; " + autoCount + " označeno za bezbedni auto-debloat.");
        updateButtons();
    }

    private void confirmSafeClean() {
        List<String> pkgs = new ArrayList<>();
        Map<String, BloatCatalog.Item> map = BloatCatalog.map();
        for (Map.Entry<String, CheckBox> e : checks.entrySet()) {
            BloatCatalog.Item item = map.get(e.getKey());
            if (item != null && item.level == BloatCatalog.Level.SAFE_AUTO) {
                pkgs.add(e.getKey());
            }
        }
        confirmAndRemove(pkgs, "Ukloniti bezbedne reklamne/telemetrijske pakete?");
    }

    private void confirmSelected() {
        List<String> pkgs = new ArrayList<>();
        for (Map.Entry<String, CheckBox> e : checks.entrySet()) {
            if (e.getValue().isChecked()) pkgs.add(e.getKey());
        }
        confirmAndRemove(pkgs, "Ukloniti označene pakete za korisnika 0?");
    }

    private void confirmAndRemove(List<String> pkgs, String question) {
        pkgs.removeIf(BloatCatalog::isHardBlocked);

        if (pkgs.isEmpty()) {
            toast("Nema paketa za uklanjanje.");
            return;
        }

        String msg = question + "\n\n" + TextUtils.join("\n", pkgs)
                + "\n\nMožeš ih kasnije vratiti dugmetom „Vrati“.";
        new AlertDialog.Builder(this)
                .setTitle("Potvrda debloat-a")
                .setMessage(msg)
                .setNegativeButton("Otkaži", null)
                .setPositiveButton("Ukloni", (d, w) -> removePackages(pkgs))
                .show();
    }

    private void removePackages(List<String> pkgs) {
        if (service == null) {
            toast("Shizuku nije povezan.");
            return;
        }
        setWorking(true);
        setStatus("Uklanjam " + pkgs.size() + " paketa...");

        new Thread(() -> {
            int ok = 0;
            List<String> failed = new ArrayList<>();

            for (String pkg : pkgs) {
                if (!validPackage(pkg) || BloatCatalog.isHardBlocked(pkg)) {
                    failed.add(pkg + " (blokirano)");
                    continue;
                }
                try {
                    String result = service.exec("pm uninstall --user 0 " + pkg);
                    if (result.contains("Success") && result.contains("__EXIT__=0")) {
                        ok++;
                        removedThisSession.add(pkg);
                    } else {
                        failed.add(pkg);
                    }
                } catch (Throwable t) {
                    failed.add(pkg);
                }
            }

            int finalOk = ok;
            runOnUiThread(() -> {
                String s = "Uklonjeno: " + finalOk + "/" + pkgs.size();
                if (!failed.isEmpty()) s += ". Nije uspelo: " + TextUtils.join(", ", failed);
                setStatus(s);
                setWorking(false);
                scan();
            });
        }).start();
    }

    private void restoreSession() {
        if (service == null) {
            toast("Shizuku nije povezan.");
            return;
        }
        if (removedThisSession.isEmpty()) {
            toast("Nema paketa uklonjenih u ovoj sesiji.");
            return;
        }

        List<String> copy = new ArrayList<>(removedThisSession);
        setWorking(true);
        setStatus("Vraćam " + copy.size() + " paketa...");

        new Thread(() -> {
            int ok = 0;
            List<String> restored = new ArrayList<>();

            for (String pkg : copy) {
                if (!validPackage(pkg)) continue;
                try {
                    String result = service.exec("cmd package install-existing --user 0 " + pkg);
                    if (result.contains("__EXIT__=0")
                            && (result.toLowerCase().contains("installed")
                                || result.toLowerCase().contains("package"))) {
                        ok++;
                        restored.add(pkg);
                    }
                } catch (Throwable ignored) {
                }
            }

            removedThisSession.removeAll(restored);
            int finalOk = ok;
            runOnUiThread(() -> {
                setStatus("Vraćeno: " + finalOk + "/" + copy.size());
                setWorking(false);
                scan();
            });
        }).start();
    }

    private boolean validPackage(String pkg) {
        return pkg != null && PKG.matcher(pkg).matches();
    }

    private void setWorking(boolean working) {
        scanBtn.setEnabled(!working && service != null);
        safeCleanBtn.setEnabled(!working && service != null && !checks.isEmpty());
        selectedCleanBtn.setEnabled(!working && service != null && !checks.isEmpty());
        restoreBtn.setEnabled(!working && service != null && !removedThisSession.isEmpty());
    }

    private void updateButtons() {
        boolean ready = service != null;
        scanBtn.setEnabled(ready);
        safeCleanBtn.setEnabled(ready && !checks.isEmpty());
        selectedCleanBtn.setEnabled(ready && !checks.isEmpty());
        restoreBtn.setEnabled(ready && !removedThisSession.isEmpty());
    }

    private void setStatus(String text) {
        runOnUiThread(() -> {
            status.setText(text);
            updateButtons();
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);

        if (userServiceArgs != null) {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }
}
