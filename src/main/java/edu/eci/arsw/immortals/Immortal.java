package edu.eci.arsw.immortals;

import edu.eci.arsw.concurrency.PauseController;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class Immortal implements Runnable {
  private final String name;
  private int health;
  private final int damage;
  private final List<Immortal> population;
  private final ScoreBoard scoreBoard;
  private final PauseController controller;
  private final boolean orderedFight;
  private volatile boolean running = true;

  public Immortal(String name, int health, int damage, String fightMode, List<Immortal> population, ScoreBoard scoreBoard, PauseController controller) {
    this.name = Objects.requireNonNull(name);
    this.health = health;
    this.damage = damage;
    this.population = Objects.requireNonNull(population);
    this.scoreBoard = Objects.requireNonNull(scoreBoard);
    this.controller = Objects.requireNonNull(controller);
    this.orderedFight = !"naive".equalsIgnoreCase(fightMode);
  }

  public String name() { return name; }
  public synchronized int getHealth() { return health; }
  public boolean isAlive() { return getHealth() > 0 && running; }
  public void stop() { running = false; }

  @Override public void run() {
    try {
      while (running && getHealth() > 0) {
        controller.awaitIfPaused();
        if (!running) break;
        var opponent = pickOpponent();
        if (opponent == null) continue;
        if (orderedFight) fightOrdered(opponent);
        else fightNaive(opponent);
        Thread.sleep(2);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private Immortal pickOpponent() {
    while (true) {
      int size = population.size();
      if (size <= 1) return null;
      int idx = ThreadLocalRandom.current().nextInt(size);
      Immortal other;
      try {
        other = population.get(idx);
      } catch (IndexOutOfBoundsException ex) {
        continue;
      }
      if (other != this) return other;
    }
  }

  private void fightNaive(Immortal other) {
    synchronized (this) {
      synchronized (other) {
        exchangeHealth(other);
      }
    }
  }

  private void fightOrdered(Immortal other) {
    Immortal first = this.name.compareTo(other.name) < 0 ? this : other;
    Immortal second = this.name.compareTo(other.name) < 0 ? other : this;
    synchronized (first) {
      synchronized (second) {
        exchangeHealth(other);
      }
    }
  }

  private void exchangeHealth(Immortal other) {
    if (this.health <= 0 || other.health <= 0) return;
    int hit = Math.min(other.health, this.damage);
    if (hit <= 0) return;
    other.health -= hit;
    this.health += hit;
    scoreBoard.recordFight();
    if (other.health <= 0) {
      other.health = 0;
      other.running = false;
      population.remove(other);
    }
  }
}
