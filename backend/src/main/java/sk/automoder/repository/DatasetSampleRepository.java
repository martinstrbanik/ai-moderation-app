package sk.automoder.repository;

import sk.automoder.model.Dataset;
import sk.automoder.model.DatasetSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetSampleRepository extends JpaRepository<DatasetSample, Long> {

    List<DatasetSample> findByDataset(Dataset dataset);

    long countByDataset(Dataset dataset);
}