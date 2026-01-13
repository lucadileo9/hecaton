package com.hecaton.task.assignment;

import com.hecaton.rmi.NodeService;
import com.hecaton.task.Task;

import java.util.List;
import java.util.Map;

/**
 * Strategy to assign tasks to workers.
 * 
 * Responsibilities:
 *   - Receive list of tasks and list of workers
 *   - Decide which worker executes which task
 *   - Return mapping workerId → list of tasks
 * 
 */
@FunctionalInterface
public interface AssignmentStrategy {
    
    /**
     * Assigns tasks to workers.
     * 
     * @param tasks list of tasks to assign
     * @param workers list of available workers (RMI stubs)
     * @return map workerId → list of assigned tasks
     * @throws IllegalArgumentException if tasks or workers are null/empty
     */
    Map<String, List<Task>> assign(List<Task> tasks, List<WorkerInfo> workers);
    
    /**
     * @return nome descrittivo della strategia (per logging)
     */
    default String getName() {
        return getClass().getSimpleName();
    }
    
    /**
     * Info minime su un worker necessarie per l'assignment.
     * Evita di passare NodeService direttamente (separa concerns).
     */
    class WorkerInfo {
        private final String workerId;
        private final NodeService nodeService;
        private final int activeTasks;  // Per LoadAware
        
        public WorkerInfo(String workerId, NodeService nodeService) {
            this(workerId, nodeService, 0);
        }
        
        public WorkerInfo(String workerId, NodeService nodeService, int activeTasks) {
            this.workerId = workerId;
            this.nodeService = nodeService;
            this.activeTasks = activeTasks;
        }
        
        public String getWorkerId() {
            return workerId;
        }
        
        public NodeService getNodeService() {
            return nodeService;
        }
        
        public int getActiveTasks() {
            return activeTasks;
        }
    }
}
