package sk.automoder.repository;

import sk.automoder.model.Dataset;
import sk.automoder.model.DatasetSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DatasetSampleRepository extends JpaRepository<DatasetSample, Long> {

    List<DatasetSample> findByDataset(Dataset dataset);

    long countByDataset(Dataset dataset);

    /** The distinct labels present in the dataset (the ground-truth class set), sorted. */
    @Query("select distinct s.expectedLabel from DatasetSample s "
            + "where s.dataset = :dataset order by s.expectedLabel")
    List<String> findDistinctLabels(@Param("dataset") Dataset dataset);
}