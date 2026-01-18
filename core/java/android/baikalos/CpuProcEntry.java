package android.baikalos;

import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public final class CpuProcEntry implements Parcelable {
    public int pid;
    public int uid;
    public String name;
    public long cpuTime;

    public CpuProcEntry() {}

    protected CpuProcEntry(Parcel in) {
        pid = in.readInt();
        uid = in.readInt();
        name = in.readString();
        cpuTime = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(pid);
        dest.writeInt(uid);
        dest.writeString(name);
        dest.writeLong(cpuTime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CpuProcEntry> CREATOR =
            new Creator<CpuProcEntry>() {
                @Override
                public CpuProcEntry createFromParcel(Parcel in) {
                    return new CpuProcEntry(in);
                }

                @Override
                public CpuProcEntry[] newArray(int size) {
                    return new CpuProcEntry[size];
                }
            };
}
