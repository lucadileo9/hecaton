package com.hecaton.cli.commands;

import com.hecaton.cli.model.TaskType;
import com.hecaton.rmi.LeaderService;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.password_crack.PasswordCrackJob;
import com.hecaton.task.sum_range.SumRangeJob;
import picocli.CommandLine.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Callable;

/**
 * Job management commands
 */
@Command(
    name = "job",
    description = "Manage distributed jobs",
    subcommands = {
        JobCommand.Submit.class,
    }
)
public class JobCommand {
    
    /**
     * Submit a new job to the cluster
     */
    @Command(name = "submit", description = "Submit a new job to the cluster")
    static class Submit implements Callable<Integer> {
        
        @Option(
            names = {"-t", "--task"},
            description = "Task type: ${COMPLETION-CANDIDATES}",
            required = true
        )
        TaskType taskType;
        
        @Option(
            names = {"--leader"},
            description = "Leader address (format: HOST:PORT, default: ${DEFAULT-VALUE})",
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        // Password Crack options
        @ArgGroup(exclusive = false, multiplicity = "0..1")
        PasswordCrackOptions passwordCrackOptions;
        
        static class PasswordCrackOptions {
            @Option(
                names = {"--hash"},
                description = "MD5/SHA hash to crack",
                required = true
            )
            String hash;
            
            @Option(
                names = {"--charset"},
                description = "Character set: digits, lowercase, uppercase, alphanumeric (default: ${DEFAULT-VALUE})",
                defaultValue = "abcdefghijklmnopqrstuvwxyz"
            )
            String charset;
            
            @Option(
                names = {"--max-length"},
                description = "Maximum password length (default: ${DEFAULT-VALUE})",
                defaultValue = "8"
            )
            int maxLength;
        }
        
        // Sum Range options
        @ArgGroup(exclusive = false, multiplicity = "0..1")
        SumRangeOptions sumRangeOptions;
        
        static class SumRangeOptions {
            @Option(
                names = {"--max-number"},
                description = "Maximum number to sum up to",
                required = true
            )
            int maxNumber;
        }
        
        @Override
        public Integer call() throws Exception {
            // 1. Validazione
            if (taskType == TaskType.PASSWORD_CRACK && passwordCrackOptions == null) {
                System.err.println("ERROR: PASSWORD_CRACK requires --hash option");
                return 1;
            }
            if (taskType == TaskType.SUM_RANGE && sumRangeOptions == null) {
                System.err.println("ERROR: SUM_RANGE requires --start and --end options");
                return 1;
            }
            
            System.out.println("========================================");
            System.out.println("  Hecaton Job Submission");
            System.out.println("========================================");
            
            // 2. Connessione RMI
            System.out.print("Connecting to Leader at " + leaderAddress + "... ");
            String[] parts = leaderAddress.split(":");
            Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
            LeaderService leader = (LeaderService) registry.lookup("leader");
            System.out.println("[OK]");
            
            // 3. Creazione Job
            Job job = null;
            System.out.print("Creating job of type " + taskType + "... ");
            if (taskType == TaskType.PASSWORD_CRACK) {
                job = new PasswordCrackJob(
                    passwordCrackOptions.hash, 
                    passwordCrackOptions.charset, 
                    passwordCrackOptions.maxLength
                );
                System.out.println("Task: Password Cracking (Hash: " + passwordCrackOptions.hash + ")");
                System.out.println("  Charset: " + passwordCrackOptions.charset);
                System.out.println("  Max Length: " + passwordCrackOptions.maxLength);
            } else if (taskType == TaskType.SUM_RANGE) {
                job = new SumRangeJob(sumRangeOptions.maxNumber);
                System.out.println("Task: Sum Range (Max: " + sumRangeOptions.maxNumber + ")");
            }
            
            System.out.println();
            System.out.println("Submitting job and waiting for completion...");
            System.out.println("(This might take a while depending on cluster size and complexity)");
            
            long startTime = System.currentTimeMillis();

            // 4. CHIAMATA BLOCCANTE
            // Qui la CLI si ferma finché il JobManager (con il suo CountDownLatch) non ritorna
            try {
                JobResult result = leader.submitJob(job); // <-- Ritorna l'oggetto completo, non solo l'ID

                long duration = System.currentTimeMillis() - startTime;
                
                // 5. Stampa Risultato Finale
                System.out.println();
                System.out.println("========================================");
                if (result.isSuccess()) {
                    System.out.println("[OK] JOB COMPLETED in " + duration + "ms");
                    System.out.println("========================================");
                    System.out.println("Job ID: " + result.getJobId());
                    System.out.println("Result: " + result.getData()); // Assumendo che JobResult abbia getData() o toString()
                } else {
                    System.out.println("[ERROR] JOB FAILED in " + duration + "ms");
                    System.out.println("========================================");
                    System.out.println("Error: " + result.getErrorMessage());
                }
                
                return result.isSuccess() ? 0 : 1;

            } catch (Exception e) {
                System.err.println();
                System.err.println("Communication Error or Timeout: " + e.getMessage());
                e.printStackTrace();
                return 2;
            }
        }
    }
}
