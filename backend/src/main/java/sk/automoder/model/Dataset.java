package sk.automoder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference dataset for benchmarks (we use existing publicly available datasets).
 */
@Entity
@Table(name = "dataset")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String source;

    /**
     * Optional mapping from this dataset's labels to the moderation verdict they imply
     * (ALLOW / BLOCK), used by the {@code MODERATION} benchmark mode. Stored as a JSON
     * array in a {@code text} column via {@link LabelRuleListConverter}. Nullable so it
     * stays compatible with {@code ddl-auto=update} on a table that already has rows.
     */
    @Convert(converter = LabelRuleListConverter.class)
    @Column(name = "label_verdicts", columnDefinition = "text")
    private List<LabelRule> labelVerdicts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ---------- getters & setters ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<LabelRule> getLabelVerdicts() {
        return labelVerdicts;
    }

    public void setLabelVerdicts(List<LabelRule> labelVerdicts) {
        this.labelVerdicts = labelVerdicts;
    }

    /**
     * @return a lookup of label -&gt; verdict for the configured {@link #getLabelVerdicts()}
     *         mapping (never {@code null}; entries with a null label or verdict are skipped).
     */
    public Map<String, PolicyAction> verdictMap() {
        Map<String, PolicyAction> out = new LinkedHashMap<>();
        if (labelVerdicts != null) {
            for (LabelRule rule : labelVerdicts) {
                if (rule != null && rule.label() != null && rule.verdict() != null) {
                    out.put(rule.label(), rule.verdict());
                }
            }
        }
        return out;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}