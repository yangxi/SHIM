package moma;


import org.jikesrvm.Options;

import static org.jikesrvm.runtime.SysCall.sysCall;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.ia32.CodeArray;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.ParallelCollector;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import probe.MomaProbe;

import static moma.MomaCmd.ProfilingApproach.*;
import static moma.MomaCmd.ProfilingPosition.*;

/**
 * Created by xiyang on 3/09/2014.
 */
public class MomaThread extends Thread {
  //what's this shim thread's running state
  public static final int MOMA_RUNNING = 1;
  public static final int MOMA_STANDBY = 2;

  //signal sources
  public static final int fpOffset = Entrypoints.momaFramePointerField.getOffset().toInt();
  public static final int execStateOffset = Entrypoints.execStatusField.getOffset().toInt();
  public static final int cmidOffset = RVMThread.momaOffset;
  public static final int gcOffset = RVMThread.momaGCoffset;

  public static String maxCPUFreq;
  public static String minCPUFreq;

  //Address are used for asking shim thread to stop profiling
  public Address controlFlag;
  //which state this profiler thread in
  public volatile int state;

  //profiler id
  public int shimid;
  //where this profiler thread working on
  public int workingHWThread;
  //what's this profiler thread's targeting hardware thread
  public int targetHWThread;
  //what's the job this profiler is going to do
  public MomaCmd curCmd;

  //log buffer returnned by native working functions
  private Address logbuf;
  //target cmid for shimYPHistogram
  private int target_cmid;
  //hardware events this profiler may count
  private static String[] shimEvents;

  //all native methods
  private static native void initShimProfiler(int numberShims, int fpOffset, int execStatOffset, int cmidOffset, int gcOffset,int targetThread);
  private static native void setTargetCPU(int cpuid, int targetcpu);
  private static native int initShimThread(int cpuid, String[] events, String outputFileName);
  private static native void shimCounting();
  private static native void shimEventHistogram(int samplingRate);
  private static native void shimFidelityHistogram(int samplingRate);
  private static native int shimCMIDHistogram(int samplingRate, int maxCMID);
  private static native int shimYPHistogram(int samplingRate, int target_cmid);
  private static native int shimTraceLog(int samplingRate, int maxCMID);
  private static native int shimTraceEvents(int samplingRate, int maxCMID);
  private static native void shimGCHistogram(int samplingRate, int maxCMID);
  private static native void shimOverheadSoftSampling(int samplingRate);
  private static native void shimOverheadSoftHistogram(int samplingRate, int maxCMID);
  private static native void shimOverheadIPCHistogram(int samplingRate);
  private static native void shimOverheadHistogram(int samplingRate, int maxCMID);


  private static native String getMaxFrequency();
  private static native String getMinFrequency();
  private static native void setCurFrequency(String newFreq);
  private static native void setPrefetcher(int cpu, long newval);



  //which CPU this shim thread is running on

  static {
    System.loadLibrary("perf_event_shim");
    int targetThread = -1;
    for (int i=0; i<RVMThread.numThreads; i++){
      RVMThread t = RVMThread.threads[i];
      if (t !=null  && t.getName().contains("org.mmtk.plan.generational.immix.GenImmixCollector [0]")) {
        System.out.println("First GenImmixCollector Thread " + t.getName() + " pthread id " + t.nativeTid);
        targetThread = t.nativeTid;
      }
    }
    initShimProfiler(MomaProbe.maxHardwareThreads, fpOffset, execStateOffset, cmidOffset, gcOffset, targetThread);
    maxCPUFreq = getMaxFrequency();
    minCPUFreq = getMinFrequency();
  }

  public MomaThread(int id, int whichHardwareThread)
  {
    shimid = id;
    workingHWThread = whichHardwareThread;
    state = MOMA_STANDBY;
  }

  public void run() {
    initThisThread();
    profile();
  }

  public void initThisThread() {
    System.out.println(" init jshim thread " + workingHWThread + "target thread" + targetHWThread);
    shimEvents = Options.MomaEvents.split(",");
    controlFlag = Address.fromIntSignExtend(initShimThread(workingHWThread, shimEvents, "/tmp/"+this.getName()));
  }


  public void profile() {
    while (true) {
      synchronized (this) {
        try {
          this.wait();
        } catch (Exception e) {
          System.out.println(e);
        }
      }
      state = MOMA_RUNNING;
      setTargetCPU(workingHWThread,targetHWThread);
      //get some work to do
      //System.out.println("Shim" + shimid + " start sampling");
      if (curCmd.cpuFreq.equals("default")){
        //setPrefetcher(targetHWThread, (long)0xf);
        //System.out.println("default config");
        ;
      } else if (curCmd.cpuFreq.equals("turnOffPrefetcher")){
        setPrefetcher(targetHWThread, (long)0xf);
      } else {
        //setPrefetcher(targetHWThread, (long)0xf);
        setCurFrequency(curCmd.cpuFreq);
      }

      switch (curCmd.shimHow) {
        case COUNTING:
          shimCounting();
          break;
        case EVENTHISTOGRAM:
          shimEventHistogram(curCmd.samplingRate);
          break;
        case FIDELITYHISTOGRAM:
          shimFidelityHistogram(curCmd.samplingRate);
          break;
        case OVERHEADHISTOGRAM:
          shimOverheadHistogram(curCmd.samplingRate,CompiledMethods.currentCompiledMethodId);
          break;
        case OVERHEADSOFTSAMPLING:
          shimOverheadSoftSampling(curCmd.samplingRate);
          break;
        case OVERHEADIPCHISTOGRAM:
          shimOverheadIPCHistogram(curCmd.samplingRate);
          break;
        case OVERHEADSOFTHISTOGRAM:
          shimOverheadSoftHistogram(curCmd.samplingRate, CompiledMethods.currentCompiledMethodId);
          break;
        case CMIDHISTOGRAM:
          System.out.println("Current last CMID " + CompiledMethods.currentCompiledMethodId);
          logbuf = Address.fromIntSignExtend(shimCMIDHistogram(curCmd.samplingRate, CompiledMethods.currentCompiledMethodId));
          break;
        case YPHISTOGRAM:
          System.out.println("YPHISTOGRAM target_cmid is " + target_cmid);
          shimYPHistogram(curCmd.samplingRate, target_cmid);
          break;
        case TRACELOG:
          System.out.println("Current last CMID " + CompiledMethods.currentCompiledMethodId);
          logbuf = Address.fromIntSignExtend(shimTraceLog(curCmd.samplingRate, CompiledMethods.currentCompiledMethodId));
          break;
        case TRACEEVENT:
          System.out.println("TraceEvent Current last CMID " + CompiledMethods.currentCompiledMethodId);
          logbuf = Address.fromIntSignExtend(shimTraceEvents(curCmd.samplingRate, CompiledMethods.currentCompiledMethodId));
          break;
        case GCHISTOGRAM:
          System.out.println("Current last CMID " + CompiledMethods.currentCompiledMethodId);
          shimGCHistogram(curCmd.samplingRate, CompiledMethods.currentCompiledMethodId);
          break;
        default:
          System.out.println("Unknown cmd: " + curCmd.shimHow.toString());
          //do nothing
      }

      if (curCmd.cpuFreq.equals("default")){
        //setPrefetcher(targetHWThread, (long)0);
       // System.out.println("Finish profiling with default config");
      } else if (curCmd.cpuFreq.equals("turnOffPrefetcher")){
        setPrefetcher(targetHWThread, (long)0);
      } else {
        //setPrefetcher(targetHWThread, (long)0);
        setCurFrequency(maxCPUFreq);
      }
      state = MOMA_STANDBY;
    }
  }

  public void suspendMoma() {
    System.out.println("Stop shim" + shimid);
    controlFlag.store(0xdead);
  }

  //log buffer format
  //BUFFER SIZE is CompiledMethods.currentCompiledMethodId
  //element size is 4 * 6 = 24 bytes
  //enach log is [[int nr_sample, int epc1,.... int epc4], ....]
  private void reportCMIDHISTOGRAMlog() {
    System.out.println("--------------ReportCMIDHISTOGRAM----------");
    System.out.println("log buf address is " + logbuf.toInt());
    int maxcmid = CompiledMethods.currentCompiledMethodId;
    int max_samples = 0;
    int max_samples_cmid = 0;
    for (int i = 1; i < maxcmid; i++) {
      Address cmidtag = logbuf.plus(i * 24);

      int nr_sample = cmidtag.loadInt();
      if (nr_sample > 0) {
        if (nr_sample > max_samples){
          max_samples = nr_sample;
          max_samples_cmid = i;
        }
        try {
          RVMMethod m = CompiledMethods.getCompiledMethod(i).getMethod();
          //CodeArray code = m.getCurrentEntryCodeArray();
          //int start = ObjectReference.fromObject(code).toAddress().toInt();
          //int start = 0;
          System.out.println(i + "," + nr_sample + "," + m.getDeclaringClass().toString() + "," + m.getName() + "," + m.getCurrentEntryCodeArray().length() + "," + ObjectReference.fromObject(m.getCurrentEntryCodeArray()).toAddress().toInt());
        } catch (Exception e) {
          System.out.println(i + " is not a valid cmid");
        }
      }
//    }
    }
    System.out.println("CMID " + max_samples_cmid + " has maximum samples " + max_samples);
    target_cmid = max_samples_cmid;
  }

  //BUF SIZE is 10 * 1024 * 1024
  //[[timestamp, cur_cmid, past_cmid, status]
  private void reportTRACELOG() {
    System.out.println("--------------ReportTRACELOG------------");
    for (int i = 0; i < 10 * 1024 * 1024; i = i+4){
      Address tag = logbuf.plus(i*4);
      int timestamp = tag.loadInt();
      int cur_cmid = tag.plus(4).loadInt();
      int past_cmid = tag.plus(8).loadInt();
      int status = tag.plus(12).loadInt();
      if (cur_cmid == 0)
        break;
      System.out.println(timestamp + "," + status + "," + cur_cmid + "," + (past_cmid >> 9) + "," + (past_cmid & 0x1ff));
    }
  }

  //BUF SIZE is 10*1024*1024
  //[timestamp,cmid,cmidyp,status,event0,...eventN],.......
  private void reportTRACEEVENT() {
    int size = 4 + shimEvents.length;
    System.out.println("--------------ReportTRACEEVENT------------");
    for (int i = 0; i < 10 * 1024 * 1024; i = i+size){
      Address tag = logbuf.plus(i*4);
      int timestamp = tag.loadInt();
      tag = tag.plus(4);
      int cur_cmid = tag.loadInt();
      if (cur_cmid == 0)
        break;
      tag = tag.plus(4);
      int past_cmid = tag.loadInt();
      tag = tag.plus(4);
      int status = tag.loadInt();
      String outstr = timestamp + "," + status + "," + cur_cmid + "," + (past_cmid >> 9) + "," + (past_cmid & 0x1ff);
      for (int j=0; j < shimEvents.length; j++){
        tag = tag.plus(4);
        outstr += "," + tag.loadInt();
      }
      System.out.println(outstr);
    }
  }

  public void reportLog(){
    if (logbuf == null) {
      System.out.println("shim" + shimid + ": logbuf is null, nothing to report");
      return;
    }

    switch (curCmd.shimHow){
      case CMIDHISTOGRAM:
        reportCMIDHISTOGRAMlog();
        break;
      case TRACELOG:
        reportTRACELOG();
        break;
      case TRACEEVENT:
        reportTRACEEVENT();
        break;
      default:
        System.out.println("shim" + shimid + ": has no idea how to report log for cmd " + curCmd.shimHow.toString());
    }
  }

  public void resetControlFlag(){
    curCmd = null;
    controlFlag.store(0x0);
  }
}
