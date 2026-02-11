package edu.eci.arsw.immortals;

import edu.eci.arsw.concurrency.PauseController;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ImmortalManager implements AutoCloseable {
  private final CopyOnWriteArrayList<Immortal> population = new CopyOnWriteArrayList<>();
  private final PauseController controller = new PauseController();
  private final ScoreBoard scoreBoard = new ScoreBoard();
  private ExecutorService exec;

  private final String fightMode;
  private final int initialHealth;
  private final int damage;
  private final long expectedTotalHealth;

  public ImmortalManager(int n, String fightMode) {
    this(n, fightMode, Integer.getInteger("health", 100), Integer.getInteger("damage", 10));
  }

  public ImmortalManager(int n, String fightMode, int initialHealth, int damage) {
    this.fightMode = fightMode;
    this.initialHealth = initialHealth;
    this.damage = damage;
    this.expectedTotalHealth = (long) n * initialHealth;
    for (int i=0;i<n;i++) {
      population.add(new Immortal("Immortal-"+i, initialHealth, damage, fightMode, population, scoreBoard, controller));
    }
  }

  public synchronized void start() {
    if (exec != null) stop();
    exec = Executors.newVirtualThreadPerTaskExecutor();
    for (Immortal im : population) {
      exec.submit(im);
    }
  }

  public void pause() {
    controller.pause();
    try {
      controller.awaitFullPause(population::size);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
  public void resume() { controller.resume(); }
  public void stop() {
    controller.resume();
    for (Immortal im : population) im.stop();
    if (exec != null) {
      exec.shutdown();
      try {
        if (!exec.awaitTermination(2, TimeUnit.SECONDS)) {
          exec.shutdownNow();
        }
      } catch (InterruptedException e) {
        exec.shutdownNow();
        Thread.currentThread().interrupt();
      } finally {
        exec = null;
      }
    }
  }

  public int aliveCount() {
    int c = 0;
    for (Immortal im : population) if (im.isAlive()) c++;
    return c;
  }

  public long totalHealth() {
    long sum = 0;
    for (Immortal im : population) sum += im.getHealth();
    return sum;
  }

  public List<Immortal> populationSnapshot() {
    return List.copyOf(population);
  }

  public ScoreBoard scoreBoard() { return scoreBoard; }
  public PauseController controller() { return controller; }
  public long expectedTotalHealth() { return expectedTotalHealth; }

  public PauseReport pauseAndReport() {
    pause();
    List<ImmortalState> snapshot = population.stream()
      .map(im -> new ImmortalState(im.name(), im.getHealth()))
      .toList();
    long sum = snapshot.stream().mapToLong(ImmortalState::health).sum();
    return new PauseReport(snapshot, sum, expectedTotalHealth, scoreBoard.totalFights());
  }

  public record ImmortalState(String name, int health) {}
  public record PauseReport(List<ImmortalState> immortals, long totalHealth, long expectedTotal, long totalFights) {}

  @Override public void close() { stop(); }
}
