package sk.automoder.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sk.automoder.service.DatasetService;

/**
 * Imports prepared benchmark datasets into the database on startup.
 */
@Component
public class DatasetInitializer implements CommandLineRunner {

    private final DatasetService datasetService;

    public DatasetInitializer(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @Override
    public void run(String... args) {
        datasetService.importIfNeeded();
    }
}