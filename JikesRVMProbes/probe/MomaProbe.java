package probe;

import moma.MomaCmd;
import moma.MomaThread;
import org.jikesrvm.Options;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;

import static org.jikesrvm.runtime.SysCall.sysCall;
import static moma.MomaCmd.ProfilingApproach.*;
import static moma.MomaCmd.ProfilingPosition.*;

public class MomaProbe implements Probe {

  //Create SHIM thread for each core
  public static final int maxHardwareThreads = 8;
  //each core has one corresponding shim working thread
  public static MomaThread[] shims;
  private static MomaThread[] remoteShimThreads;
  private static MomaThread sameShimThread;
  //which CPU JikesRVM process are bind on
  public static int runningCPU = 4;




  public void init(){
    MomaCmd.parseShimCommand();
    long cpumask = sysCall.sysCall.sysGetThreadBindSet(Magic.getThreadRegister().pthread_id);
    for (int i=0; i<64; i++) {
      if ((cpumask & (1 << i)) != 0) {
        runningCPU = i;
        break;
      }
    }
    System.out.println("SHIM:App is running on CPU " + runningCPU);
    shims = new MomaThread[maxHardwareThreads];
    remoteShimThreads = new MomaThread[maxHardwareThreads - 1];
    int remoteIndex = 0;
    for (int i = 0; i < maxHardwareThreads; i++) {
      shims[i] = new MomaThread(i, i);
      if (i + maxHardwareThreads/2 == runningCPU) {
        sameShimThread = shims[i];
      }else{
        remoteShimThreads[remoteIndex++] = shims[i];
      }
      shims[i].setName("shimCPU" + i);
      shims[i].start();
    }
  }

  private void launchShims() {
    for (MomaThread t : shims){
      if (t.curCmd != null){
        synchronized (t) {
          t.notifyAll();
        }
      }
    }
  }

  private void waitForShims(){

    for (MomaThread t : shims) {
      //suspend the shim thread if it is running.
      if (t.curCmd == null)
        continue;
      t.suspendMoma();
      while (t.state == t.MOMA_RUNNING)
        ;
      t.reportLog();
      t.resetControlFlag();
    }
  }

  public void cleanup() {
    System.err.println("MomaProbe.cleanup()");
  }

  public void begin(String benchmark, int iteration, boolean warmup) {
    System.out.println("MomaProbe.begin(benchmark = " + benchmark + ", iteration = " + iteration + ", warmup = " + warmup + ")");
    //any cmds we have to do for this iteration?
    int remoteIndex = 0;
    int anyprofiler = 0;

    for (int i=0; i < MomaCmd.shimCmds.length; i++){
      MomaCmd c = MomaCmd.shimCmds[i];
      if (c.shimWhen == iteration){
        MomaThread t = null;
        if (c.shimWhere == REMOTECORE) {
          t = remoteShimThreads[remoteIndex++];
        } else if (c.shimWhere == SAMECORE){
          t = sameShimThread;
        }
        t.curCmd = c;
        t.targetHWThread = runningCPU;
        anyprofiler++;
      }
    }
    if (anyprofiler > 0)
      launchShims();
  }

  public void end(String benchmark, int iteration, boolean warmup) {
    System.out.println("MomaProbe.end(benchmark = " + benchmark + ", iteration = " + iteration + ", warmup = " + warmup + ")");
    waitForShims();
  }

  public void report(String benchmark, int iteration, boolean warmup) {
    System.out.println("MomaProbe.report(benchmark = " + benchmark + ", iteration = " + iteration + ", warmup = " + warmup + ")");
  }
}
