/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.taskprocessor;

import com.google.common.base.Optional;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.ce.log.CeLogging;
import org.sonar.ce.queue.CeTask;
import org.sonar.ce.queue.CeTaskResult;
import org.sonar.ce.taskprocessor.CeTaskProcessor;
import org.sonar.core.util.logs.Profiler;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.server.computation.queue.InternalCeQueue;

import static java.lang.String.format;
import static org.sonar.ce.log.CeLogging.logCeActivity;

public class CeWorkerCallableImpl implements CeWorkerCallable {

  private static final Logger LOG = Loggers.get(CeWorkerCallableImpl.class);

  private final InternalCeQueue queue;
  private final CeLogging ceLogging;
  private final CeTaskProcessorRepository taskProcessorRepository;

  public CeWorkerCallableImpl(InternalCeQueue queue, CeLogging ceLogging, CeTaskProcessorRepository taskProcessorRepository) {
    this.queue = queue;
    this.ceLogging = ceLogging;
    this.taskProcessorRepository = taskProcessorRepository;
  }

  @Override
  public Boolean call() throws Exception {
    Optional<CeTask> ceTask = tryAndFindTaskToExecute();
    if (!ceTask.isPresent()) {
      return false;
    }

    executeTask(ceTask.get());
    return true;
  }

  private Optional<CeTask> tryAndFindTaskToExecute() {
    try {
      return queue.peek();
    } catch (Exception e) {
      LOG.error("Failed to pop the queue of analysis reports", e);
    }
    return Optional.absent();
  }

  private void executeTask(CeTask task) {
    // logging twice: once in sonar.log and once in CE appender
    Profiler regularProfiler = startProfiler(task);
    ceLogging.initForTask(task);
    Profiler ceProfiler = startActivityProfiler(task);

    CeActivityDto.Status status = CeActivityDto.Status.FAILED;
    CeTaskResult process = null;
    try {
      // TODO delegate the message to the related task processor, according to task type
      Optional<CeTaskProcessor> taskProcessor = taskProcessorRepository.getForCeTask(task);
      if (taskProcessor.isPresent()) {
        process = taskProcessor.get().process(task);
        status = CeActivityDto.Status.SUCCESS;
      } else {
        LOG.error("No CeTaskProcessor is defined for task of type {}. Plugin configuration may have changed", task.getType());
        status = CeActivityDto.Status.FAILED;
      }
    } catch (Throwable e) {
      LOG.error(format("Failed to execute task %s", task.getUuid()), e);
    } finally {
      queue.remove(task, status, process);
      // logging twice: once in sonar.log and once in CE appender
      stopActivityProfiler(ceProfiler, task, status);
      ceLogging.clearForTask();
      stopProfiler(regularProfiler, task, status);
    }
  }

  private static Profiler startProfiler(CeTask task) {
    Profiler profiler = Profiler.create(LOG);
    addContext(profiler, task);
    return profiler.startDebug("Execute task");
  }

  private static Profiler startActivityProfiler(CeTask task) {
    Profiler profiler = Profiler.create(LOG);
    addContext(profiler, task);
    return logCeActivity(() -> profiler.startInfo("Execute task"));
  }

  private static void stopProfiler(Profiler profiler, CeTask task, CeActivityDto.Status status) {
    if (!profiler.isDebugEnabled()) {
      return;
    }

    addContext(profiler, task);
    if (status == CeActivityDto.Status.FAILED) {
      profiler.stopError("Executed task");
    } else {
      profiler.stopDebug("Executed task");
    }
  }

  private static void stopActivityProfiler(Profiler profiler, CeTask task, CeActivityDto.Status status) {
    addContext(profiler, task);
    if (status == CeActivityDto.Status.FAILED) {
      logCeActivity(() -> profiler.stopError("Executed task"));
    } else {
      logCeActivity(() -> profiler.stopInfo("Executed task"));
    }
  }

  private static void addContext(Profiler profiler, CeTask task) {
    profiler
      .logTimeLast(true)
      .addContext("project", task.getComponentKey())
      .addContext("type", task.getType())
      .addContext("id", task.getUuid());
    String submitterLogin = task.getSubmitterLogin();
    if (submitterLogin != null) {
      profiler.addContext("submitter", submitterLogin);
    }
  }

}
