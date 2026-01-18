/**
 */

package android.baikalos;

import android.baikalos.BaikalAppProfile;
import android.os.Bundle;


/**
 * API to baikal manager service.
 *
 */
interface IBaikalService {
    @RequiresNoPermission
    int getBaikalPackageOption(String packageName, int uid, int opCode,int def);
    @RequiresNoPermission
    String getBaikalPackageOptionString(String packageName, int uid, int opCode, String def);
    @RequiresNoPermission
    int getBaikalOption(int opCode,int def, int callingUid, String callingPackage);
    @RequiresNoPermission
    int getBaikalOptionWithParams(int opCode,int def, int callingUid, String callingPackage, in Bundle params);
    @RequiresNoPermission
    String getBaikalOptionString(int opCode,String def, int callingUid, String callingPackage);
    @RequiresNoPermission
    String getBaikalOptionStringWithParams(int opCode,String def, int callingUid, String callingPackage, in Bundle params);
    @RequiresNoPermission
    BaikalAppProfile getBaikalAppProfile(String packageName, int uid);
    @RequiresNoPermission
    BaikalAppProfile getBaikalAppProfileNotNull(String packageName, int uid);
    @RequiresNoPermission
    int getBaikalSettingInt(int realm,in String name, int def);
    @RequiresNoPermission
    String getBaikalSettingString(int realm,in String name, in String def);
}
