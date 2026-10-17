import cn.local.notifyledger.UpdatePolicy;
public class UpdatePolicyTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        check(UpdatePolicy.newer(3,4));check(!UpdatePolicy.newer(4,4));check(!UpdatePolicy.newer(5,4));check(!UpdatePolicy.newer(4,Long.MAX_VALUE));
        String url="https://github.com/Debbie-anan/daily-spending/releases/download/v0.4/app-release.apk";
        check(UpdatePolicy.validApkUrl(url));check(!UpdatePolicy.validApkUrl(url.replace("https:","http:")));check(!UpdatePolicy.validApkUrl(url.replace("Debbie-anan","someone")));check(!UpdatePolicy.validApkUrl(url+"?x=1"));
        check(UpdatePolicy.validHash(new String(new char[64]).replace('\0','a')));check(!UpdatePolicy.validHash("abcd"));check(!UpdatePolicy.validHash(new String(new char[64]).replace('\0','g')));
        System.out.println("11 update-policy checks passed");
    }
}
