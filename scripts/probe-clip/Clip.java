// Field-probe helper (SEARCH-ML-1): puts text on the emulator's clipboard from the adb shell, the way scrcpy does
// (the clipboard service has no shell command). Run: CLASSPATH=/data/local/tmp/clip.dex app_process / Clip "<text>"
// "\n" in the argument becomes a real line break. Test tooling only; never part of the app.
import android.content.ClipData;
import android.os.IBinder;
import java.lang.reflect.Method;

public class Clip {
    public static void main(String[] args) throws Exception {
        String text = args[0].replace("\\n", "\n");
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "clipboard");
        Object svc = Class.forName("android.content.IClipboard$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        ClipData clip = ClipData.newPlainText("probe", text);
        for (Method m : svc.getClass().getMethods()) {
            if (!m.getName().equals("setPrimaryClip")) continue;
            Class<?>[] p = m.getParameterTypes();
            Object[] a = new Object[p.length];
            a[0] = clip;
            int strings = 0;
            for (int i = 1; i < p.length; i++) {
                if (p[i] == String.class) a[i] = strings++ == 0 ? "com.android.shell" : null;
                else if (p[i] == int.class) a[i] = 0;
            }
            m.invoke(svc, a);
            System.out.println("clip set (" + p.length + " args), " + text.split("\n", -1).length + " lines");
            return;
        }
        throw new IllegalStateException("no setPrimaryClip");
    }
}
