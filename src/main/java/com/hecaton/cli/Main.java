package com.hecaton.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point per Hecaton Distributed Computing System
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("==================================");
        logger.info("Hecaton - Distributed Computing");
        logger.info("==================================");
        
        // TODO: Parsing argomenti (--port, --leader, --join)
        logger.info("Sistema avviato correttamente!");
        logger.info("Fase 0 completata: Maven setup verificato");
    }
}
