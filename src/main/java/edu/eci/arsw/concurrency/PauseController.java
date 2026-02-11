package edu.eci.arsw.concurrency;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

public final class PauseController {
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition unpaused = lock.newCondition();
  private final Condition pauseReached = lock.newCondition();
  private volatile boolean paused = false;
  private int pausedWorkers = 0;

  public void pause() { lock.lock(); try { paused = true; } finally { lock.unlock(); } }
  public void resume() { lock.lock(); try { paused = false; unpaused.signalAll(); } finally { lock.unlock(); } }
  public boolean paused() { return paused; }

  public void awaitFullPause(IntSupplier expectedSupplier) throws InterruptedException {
    lock.lock();
    try {
      while (paused) {
        int expected = Math.max(0, expectedSupplier.getAsInt());
        if (expected == 0 || pausedWorkers >= expected) {
          return;
        }
        pauseReached.await();
      }
    } finally { lock.unlock(); }
  }

  public void awaitIfPaused() throws InterruptedException {
    lock.lockInterruptibly();
    try {
      boolean registered = false;
      while (paused) {
        if (!registered) {
          pausedWorkers++;
          registered = true;
          pauseReached.signalAll();
        }
        unpaused.await();
      }
      if (registered) {
        pausedWorkers--;
        pauseReached.signalAll();
      }
    } finally { lock.unlock(); }
  }
}
