package moma;

import org.jikesrvm.Options;

import static moma.MomaCmd.ProfilingPosition.REMOTECORE;
import static moma.MomaCmd.ProfilingPosition.SAMECORE;

/**
 * Created by xiyang on 13/04/15.
 * Command information for the profiler
 */
public class MomaCmd {
  public static enum ProfilingApproach {
    EVENTHISTOGRAM, CMIDHISTOGRAM, COUNTING, LOGGING,  GCHISTOGRAM, FIDELITYHISTOGRAM,
    OVERHEADSOFTSAMPLING,OVERHEADSOFTHISTOGRAM,OVERHEADIPCHISTOGRAM,OVERHEADHISTOGRAM,
    TRACELOG, YPHISTOGRAM, TRACEEVENT, NOTHING,
  }

  public static enum ProfilingPosition {
    SAMECORE, REMOTECORE
  }


  public static MomaCmd[] shimCmds;

  //which iteration this cmd is running
  public int shimWhen;
  //how to profile the application
  public ProfilingApproach shimHow;
  //where to put the profiler
  public ProfilingPosition shimWhere;
  //what's the cpu frequency we want to use
  public String cpuFreq;
  //what's the sampling frequency
  int samplingRate;

  private MomaCmd(int when, ProfilingPosition where, ProfilingApproach how, int rate, String freq) {
    shimWhen = when;
    shimHow = how;
    shimWhere = where;
    cpuFreq = freq;
    samplingRate = rate;
  }
  //momaApproach should be iteration:iteration:...
  // iteration:"iteration,[remoteCore|sameCore],[histogram|hardonly|softonly|logging],rate,[cpuFrequency]"

  public static void parseShimCommand() {
    String[] its = Options.MomaApproach.split(":");
    shimCmds = new MomaCmd[its.length];
    for (int i = 0; i < its.length; i++) {
      String[] cmds = its[i].split(",");
      System.out.println("Parse command for iteration " + i + " : " + its[i]);

      int when = Integer.parseInt(cmds[0]);

      ProfilingPosition where = SAMECORE;
      if (cmds[1].equals("remote")) {
        where = REMOTECORE;
      }

      String n = cmds[2];
      ProfilingApproach how = ProfilingApproach.NOTHING;


      if (n.equals("eventHistogram")) {
        how = ProfilingApproach.EVENTHISTOGRAM;
      } else if (n.equals("cmidHistogram")) {
        how = ProfilingApproach.CMIDHISTOGRAM;
      } else if (n.equals("fidelityHistogram")) {
        how = ProfilingApproach.FIDELITYHISTOGRAM;
      } else if (n.equals("gcHistogramLog")) {
        how = ProfilingApproach.GCHISTOGRAM;
      } else if (n.equals("gcHistogram")){
        how = ProfilingApproach.GCHISTOGRAM;
      } else if (n.equals("counting")) {
        how = ProfilingApproach.COUNTING;
      } else if (n.equals("logging")) {
        how = ProfilingApproach.LOGGING;
      } else if (n.equals("overheadSoftSampling")) {
        how = ProfilingApproach.OVERHEADSOFTSAMPLING;
      } else if (n.equals("overheadSoftHistogram")) {
        how = ProfilingApproach.OVERHEADSOFTHISTOGRAM;
      } else if (n.equals("overheadIPCHistogram")) {
        how = ProfilingApproach.OVERHEADIPCHISTOGRAM;
      } else if (n.equals("overheadHistogram")) {
        how = ProfilingApproach.OVERHEADHISTOGRAM;
      } else if (n.equals("traceLog")) {
        how = ProfilingApproach.TRACELOG;
      } else if (n.equals("traceEvents")) {
        how = ProfilingApproach.TRACEEVENT;
      } else if (n.equals("ypHistogram")) {
        how = ProfilingApproach.YPHISTOGRAM;
      } else {
        System.out.println("Unknown profiling approach:" + Options.MomaApproach);
        how = ProfilingApproach.NOTHING;
      }

      int rate = Integer.parseInt(cmds[3]);
      String freq = cmds[4];
      shimCmds[i] = new MomaCmd(when,where, how, rate, freq);
      System.out.println("Iteration" + i + " CMD " + when + "," + where + "," + how + "," + rate + "," + freq);
    }
  }


}


