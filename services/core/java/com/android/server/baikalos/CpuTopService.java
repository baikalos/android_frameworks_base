package com.android.server.baikalos;

import android.baikalos.CpuProcEntry;
import android.baikalos.ICpuTopService;

import com.android.internal.os.ProcessCpuTracker;

import java.util.ArrayList;
import java.util.List;

public final class CpuTopService extends ICpuTopService.Stub {

    private final ProcessCpuTracker mCpuTracker =
            new ProcessCpuTracker(true);

    public CpuTopService() {
        mCpuTracker.init();
    }

    @Override
    public synchronized List<CpuProcEntry> getCpuTop() {
        mCpuTracker.update();

        ArrayList<CpuProcEntry> out = new ArrayList<>();

        final int N = mCpuTracker.countStats();
        for (int i = 0; i < N; i++) {
            ProcessCpuTracker.Stats st = mCpuTracker.getStats(i);
            if (!st.working) continue;

            CpuProcEntry e = new CpuProcEntry();
            e.pid = st.pid;
            e.uid = st.uid;
            e.name = st.name;
            e.cpuTime = st.rel_utime + st.rel_stime;

            out.add(e);
        }

        out.sort((a, b) -> Long.compare(b.cpuTime, a.cpuTime));
        return out;
    }
}

