package com.merceria153.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {

    private final ProductoService productoSvc;

    public CleanupJob(ProductoService productoSvc) {
        this.productoSvc = productoSvc;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupInactivos() {
        int deleted = productoSvc.cleanupInactivos(30);
        System.out.println("[CleanupJob] Eliminados " + deleted + " productos inactivos de más de 30 días");
    }
}