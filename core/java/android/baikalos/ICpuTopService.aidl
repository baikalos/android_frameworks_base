package android.baikalos;

import android.baikalos.CpuProcEntry;

/** @hide */
interface ICpuTopService {
    List<CpuProcEntry> getCpuTop();
}
