package cn.local.notifyledger;

public final class UpdatePolicy {
    public static final long MAX_APK_BYTES=50L*1024*1024;
    public static boolean validApkUrl(String url) {
        return url!=null && url.startsWith("https://github.com/Debbie-anan/daily-spending/releases/download/") && url.endsWith("/app-release.apk") && !url.contains("..") && !url.contains("?");
    }
    public static boolean validHash(String hash) {return hash!=null && hash.matches("[a-fA-F0-9]{64}");}
    public static boolean newer(long installed,long candidate) {return candidate>installed && candidate<=Integer.MAX_VALUE;}
}
