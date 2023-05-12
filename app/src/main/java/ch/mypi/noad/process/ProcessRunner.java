package ch.mypi.noad.process;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ProcessRunner implements Runnable {
  private static final String LOG_PREFIX = "ProcessRunner";

  private final File workingDirectory;
  private final Map<String, String> environment;
  private final String[] args;

  // kill process forcefully if it doesn't return in a fair use of time.
  @Nullable
  private Timer killTimer;
  @Nullable
  private Process currentProcess;
  @NonNull
  private Consumer<String> stdoutHandler = (_l) -> {
  };
  @NonNull
  private Consumer<String> stderrHandler = (_l) -> {
  };
  @NonNull
  private CompletableFuture<Void> awaitStarted;

  private boolean stopping = false;

  /**
   * Wraps a process into a controllable object.
   */
  public ProcessRunner(@NonNull File workingDirectory, @NonNull Map<String, String> environment, String... args) {
    this.workingDirectory = workingDirectory;
    this.environment = environment;
    this.args = args;
    Log.i(LOG_PREFIX, "STARTING, workingDir: " + workingDirectory);
    Log.i(LOG_PREFIX, "STARTING, args: " + String.join(",", args));
    awaitStarted = new CompletableFuture<>();
  }

  private void reset() {
    awaitStarted.cancel(true);
    awaitStarted = new CompletableFuture<>();
    this.stopping = false;
    if (this.killTimer != null) {
      this.killTimer.cancel();
    }
  }

  private int waitFor(@NonNull Process process) {
    var inStream = process.getInputStream();
    var inErrStream = process.getErrorStream();
    var outStream = process.getOutputStream();

    // Handling stdout thread
    Thread outStreamReader = new Thread(() -> {
      try {
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(inStream));
        while ((line = in.readLine()) != null) {
          stdoutHandler.accept(line);
        }
      } catch (IOException e) {
        // Exception is expected if process gets killed.
        Log.d(LOG_PREFIX, "READ_STDOUT_FAILED");
      } catch (Exception e) {
        Log.w(LOG_PREFIX, "READ_STDOUT_IO_FAILED", e);
      }
    });
    outStreamReader.start();

    // Handling stderr thread
    Thread errStreamReader = new Thread(() -> {
      try {
        String line;
        BufferedReader inErr = new BufferedReader(new InputStreamReader(inErrStream));
        while ((line = inErr.readLine()) != null) {
          stderrHandler.accept(line);
        }
      } catch (IOException e) {
        // Exception is expected if process gets killed.
        Log.d(LOG_PREFIX, "READ_STDERR_FAILED");
      } catch (Exception e) {
        Log.w(LOG_PREFIX, "READ_STDERR_IO_FAILED", e);
      }
    });
    errStreamReader.start();

    int retValue = 255;
    try {
      retValue = process.waitFor();
    } catch (InterruptedException e) {
      Log.w(LOG_PREFIX, "WAIT_FOR_PROCESS_INTERRUPTED", e);
    } finally {
      try {
        outStream.close();
      } catch (IOException e) {
        Log.e(LOG_PREFIX, "STREAM_CLOSE_FAILED", e);
      }
    }
    return retValue;
  }

  public boolean isAlive() {
    if (this.currentProcess != null) {
      return this.currentProcess.isAlive();
    }
    return false;
  }

  public void stop(long timeout, TimeUnit unit) {
    Log.d(LOG_PREFIX, "STOPPING");
    stopping = true;
    if (this.currentProcess != null) {
      this.killTimer = new Timer();
      TimerTask kill = new TimerTask() {
        @Override
        public void run() {
          if (currentProcess.isAlive()) {
            Log.i(LOG_PREFIX, "DESTROY_PROCESS_FORCIBLY");
            currentProcess.destroyForcibly();
            Log.i(LOG_PREFIX, "SIGKILL_SENT");
          }
        }
      };
      this.currentProcess.destroy();
      Log.i(LOG_PREFIX, "SIGTERM_SENT");
      try {
        this.killTimer.schedule(kill, unit.toMillis(timeout));
      } catch (IllegalStateException e) {
        Log.d(LOG_PREFIX, "SCHEDULE_KILL_TIMER_FAILED", e);
      }
    }
  }

  public void run() {
    ProcessBuilder processBuilder = new ProcessBuilder(args);
    processBuilder.environment().putAll(environment);

    try {
      // see https://developer.android.com/reference/java/lang/ProcessBuilder
      var currentProcess = processBuilder.directory(workingDirectory).start();
      Log.i(LOG_PREFIX, "PROCESS_STARTED");
      this.currentProcess = currentProcess;
      this.awaitStarted.complete(null);

      int r = waitFor(currentProcess);
      Log.i(LOG_PREFIX, "PROCESS_EXITED, exitCode: " + r);
      if (r != 0 && !stopping) {
        throw new IllegalStateException("Process terminated exceptionally (" + r + ")");
      }
    } catch (IOException e) {
      this.awaitStarted.completeExceptionally(e);
      throw new IllegalStateException(e);
    } finally {
      this.reset();
    }
  }

  public void setStdoutHandler(@NonNull Consumer<String> logLine) {
    this.stdoutHandler = logLine;
  }

  public void setStderrHandler(@NonNull Consumer<String> logLine) {
    this.stderrHandler = logLine;
  }

  public CompletableFuture<Void> awaitStarted() {
    return this.awaitStarted;
  }
}
