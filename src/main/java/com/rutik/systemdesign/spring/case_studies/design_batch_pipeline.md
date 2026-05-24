# ETL Batch Pipeline: 10 Million Records Nightly with Spring Batch

## Problem Statement

Design an ETL pipeline that processes 10 million CSV records nightly (2 AM–5 AM window, 3-hour budget) from CSV files into a PostgreSQL database. The system must:

- Read from a directory of CSV files totaling ~5 GB
- Validate and transform each record (data type conversion, business rule validation, enrichment from a reference table)
- Write transformed records to PostgreSQL in bulk
- Support restart from the last successful checkpoint if the job fails midway
- Skip malformed records (up to 1% of total) and log them to a rejected-records file without failing the job
- Process records in parallel across multiple partitions
- Expose job metrics (records processed, skipped, timing) via Micrometer

Constraints: Spring Batch 5.x, PostgreSQL 15, Java 21, single-machine deployment (8-core server, 32 GB RAM). Target throughput: 3.5 million records/hour (~1,000 records/second sustained).

---

## Architecture Overview

```
 Nightly Trigger (cron: 0 2 * * *)
          |
          v
 [JobLauncher] --> [ETL Job]
                        |
              +---------+---------+
              |                   |
     [Master Step]        [Job Listeners]
     (Partitioner)        (metrics, alerts)
              |
    +---------+---------+---------+
    |         |         |         |
 [Worker   [Worker   [Worker   [Worker
  Step 1]   Step 2]   Step 3]   Step 4]
  Part 0    Part 1    Part 2    Part 3
    |         |         |         |
    v         v         v         v
 [FlatFileItemReader per partition]
    |
    v
 [ValidatingItemProcessor]
    + BusinessRuleProcessor
    + EnrichmentProcessor
    |
    v
 [JdbcBatchItemWriter] -- bulk INSERT (1000 rows per batch)
    |
    v
 [PostgreSQL: records table]

  JobRepository: stores step execution state for restart
  [H2 / PostgreSQL: batch_* tables]
```

---

## Key Design Decisions

### 1. Partitioned Processing over Single-Thread Sequential Read

A single-threaded sequential read of 10 million records at 1,000 records/second would take ~2.78 hours — barely within the 3-hour window with no margin for failures or retries. Partitioned processing splits the input files across 8 worker partitions running in parallel on separate threads, each handling ~1.25 million records. This achieves the throughput target with a comfortable margin.

### 2. Chunk-Oriented Processing for Transactional Safety

Each chunk (1,000 records) is read, processed, and written in a single database transaction. If the write fails, only that chunk's 1,000 records are rolled back — not the entire job. This is Spring Batch's core reliability model. Chunk size of 1,000 was chosen by benchmarking: smaller chunks (100) resulted in too many short transactions with high per-transaction overhead; larger chunks (10,000) increased the reprocessing cost on retry and held database locks longer.

### 3. Job Repository for Restart Capability

Spring Batch's `JobRepository` persists the state of every `StepExecution` (records read, written, skipped) to the database. If the job fails at partition 3, step execution state shows that partitions 0, 1, 2 completed successfully. On relaunch with the same `JobParameters`, Spring Batch skips completed steps and resumes only the failed/incomplete partitions. This is critical for a nightly job — if it fails at 4 AM, it must be restartable without reprocessing already-loaded records.

### 4. Skip Policy over Fail-Fast for Data Quality

Business requirement: up to 1% malformed records (100,000 records) should not fail the entire job. Spring Batch's skip mechanism catches specific exceptions during read, process, or write phases and increments a skip counter. When the skip limit is exceeded, the job fails. Skipped items are logged to a separate rejected-records CSV file via an `ItemSkipListener` for downstream data quality review.

### 5. File-Based Partitioning over Range-Based Partitioning

Since the input is multiple CSV files, partitioning by file (one partition per file, up to 8 files) is simpler and avoids seeking within files. If the number of files exceeds 8, a `MultiResourceItemReader` within each partition handles multiple files. Range-based partitioning (line number ranges within a single large file) was rejected because seeking to byte offsets in compressed files is complex and `FlatFileItemReader` does not support range-based reads natively.

---

## Implementation

### Domain Objects

```java
package com.rutik.systemdesign.spring.batch;

// Raw CSV record — String fields to handle malformed data gracefully
public class RawCustomerRecord {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String dateOfBirth;  // String — may be malformed
    private String countryCode;
    private String annualRevenue;  // String — may be non-numeric
    private int lineNumber;

    // Getters and setters omitted for brevity
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getAnnualRevenue() { return annualRevenue; }
    public void setAnnualRevenue(String annualRevenue) { this.annualRevenue = annualRevenue; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
}
```

```java
package com.rutik.systemdesign.spring.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

// Transformed and validated record ready for database insert
public class CustomerRecord {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate dateOfBirth;
    private String countryCode;
    private String countryName;  // Enriched from reference data
    private BigDecimal annualRevenue;
    private String customerTier;  // Derived: BRONZE/SILVER/GOLD based on revenue

    // Getters and setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public BigDecimal getAnnualRevenue() { return annualRevenue; }
    public void setAnnualRevenue(BigDecimal annualRevenue) { this.annualRevenue = annualRevenue; }
    public String getCustomerTier() { return customerTier; }
    public void setCustomerTier(String customerTier) { this.customerTier = customerTier; }
}
```

### Job Configuration

```java
package com.rutik.systemdesign.spring.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CustomerEtlJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerItemProcessor customerItemProcessor;
    private final CustomerItemWriter customerItemWriter;
    private final CustomerPartitioner customerPartitioner;
    private final JobCompletionListener jobCompletionListener;
    private final CustomerSkipListener customerSkipListener;

    public CustomerEtlJobConfig(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 CustomerItemProcessor customerItemProcessor,
                                 CustomerItemWriter customerItemWriter,
                                 CustomerPartitioner customerPartitioner,
                                 JobCompletionListener jobCompletionListener,
                                 CustomerSkipListener customerSkipListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerItemProcessor = customerItemProcessor;
        this.customerItemWriter = customerItemWriter;
        this.customerPartitioner = customerPartitioner;
        this.jobCompletionListener = jobCompletionListener;
        this.customerSkipListener = customerSkipListener;
    }

    @Bean
    public Job customerEtlJob() {
        return new JobBuilder("customerEtlJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(jobCompletionListener)
            .start(masterStep())
            .build();
    }

    @Bean
    public Step masterStep() {
        return new StepBuilder("masterStep", jobRepository)
            .partitioner("workerStep", customerPartitioner)
            .step(workerStep())
            .gridSize(8)  // 8 partitions = 8 concurrent threads
            .taskExecutor(partitionTaskExecutor())
            .build();
    }

    @Bean
    public Step workerStep() {
        return new StepBuilder("workerStep", jobRepository)
            .<RawCustomerRecord, CustomerRecord>chunk(1000, transactionManager)
            .reader(customerItemReader(null))  // null — file resource injected per partition
            .processor(customerItemProcessor)
            .writer(customerItemWriter)
            // Skip malformed records — up to 100,000 (1% of 10M)
            .faultTolerant()
            .skipLimit(100_000)
            .skip(ValidationException.class)
            .skip(org.springframework.batch.item.file.FlatFileParseException.class)
            // Retry on transient DB errors
            .retryLimit(3)
            .retry(org.springframework.dao.TransientDataAccessException.class)
            .listener(customerSkipListener)
            .build();
    }

    // ItemReader is step-scoped because each partition gets its own file resource
    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<RawCustomerRecord> customerItemReader(
            @org.springframework.beans.factory.annotation.Value("#{stepExecutionContext['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<RawCustomerRecord>()
            .name("customerItemReader")
            .resource(new FileSystemResource(filePath))
            .delimited()
            .delimiter(",")
            .names("customerId", "firstName", "lastName", "email",
                   "dateOfBirth", "countryCode", "annualRevenue")
            .linesToSkip(1)  // Skip CSV header row
            .targetType(RawCustomerRecord.class)
            .build();
    }

    @Bean
    public TaskExecutor partitionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(0);  // No queuing — partitions start immediately
        executor.setThreadNamePrefix("batch-partition-");
        executor.initialize();
        return executor;
    }
}
```

### Partitioner — Splits Files Across Workers

```java
package com.rutik.systemdesign.spring.batch;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CustomerPartitioner implements Partitioner {

    @Value("${batch.input.directory:/data/customers/input}")
    private String inputDirectory;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        File dir = new File(inputDirectory);
        File[] csvFiles = dir.listFiles((d, name) -> name.endsWith(".csv"));

        if (csvFiles == null || csvFiles.length == 0) {
            throw new IllegalStateException("No CSV files found in: " + inputDirectory);
        }

        List<File> files = Arrays.asList(csvFiles);
        Map<String, ExecutionContext> partitions = new HashMap<>();

        // Distribute files across partitions — if more files than partitions,
        // group multiple files into one partition
        for (int i = 0; i < Math.min(files.size(), gridSize); i++) {
            ExecutionContext context = new ExecutionContext();
            context.putString("filePath", files.get(i).getAbsolutePath());
            context.putString("partitionId", "partition-" + i);
            partitions.put("partition-" + i, context);
        }

        return partitions;
    }
}
```

### Item Processor (Validation + Enrichment)

```java
package com.rutik.systemdesign.spring.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CustomerItemProcessor implements ItemProcessor<RawCustomerRecord, CustomerRecord> {

    private static final Logger log = LoggerFactory.getLogger(CustomerItemProcessor.class);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Pre-loaded country reference data — avoids DB call per record
    private final Map<String, String> countryNameCache;

    public CustomerItemProcessor(CountryReferenceService countryReferenceService) {
        // Load all country codes at startup to avoid per-record DB lookups
        this.countryNameCache = countryReferenceService.loadAllCountries();
    }

    @Override
    public CustomerRecord process(RawCustomerRecord raw) throws Exception {
        // Return null to filter out the record — Spring Batch will not pass null to the writer
        // but here we throw to trigger the skip mechanism instead

        validateRequired(raw);
        validateEmail(raw.getEmail());

        CustomerRecord customer = new CustomerRecord();
        customer.setCustomerId(raw.getCustomerId().trim());
        customer.setFirstName(capitalize(raw.getFirstName()));
        customer.setLastName(capitalize(raw.getLastName()));
        customer.setEmail(raw.getEmail().trim().toLowerCase());
        customer.setDateOfBirth(parseDate(raw.getDateOfBirth(), raw.getLineNumber()));
        customer.setCountryCode(raw.getCountryCode().trim().toUpperCase());

        // Enrich from pre-loaded cache — no DB call
        String countryName = countryNameCache.get(customer.getCountryCode());
        if (countryName == null) {
            throw new ValidationException("Unknown country code: " + customer.getCountryCode() +
                                          " at line " + raw.getLineNumber());
        }
        customer.setCountryName(countryName);

        BigDecimal revenue = parseRevenue(raw.getAnnualRevenue(), raw.getLineNumber());
        customer.setAnnualRevenue(revenue);
        customer.setCustomerTier(deriveCustomerTier(revenue));

        return customer;
    }

    private void validateRequired(RawCustomerRecord raw) {
        if (isBlank(raw.getCustomerId()))
            throw new ValidationException("Missing customerId at line " + raw.getLineNumber());
        if (isBlank(raw.getEmail()))
            throw new ValidationException("Missing email at line " + raw.getLineNumber());
        if (isBlank(raw.getCountryCode()))
            throw new ValidationException("Missing countryCode at line " + raw.getLineNumber());
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("Invalid email format: " + email);
        }
    }

    private LocalDate parseDate(String dateStr, int lineNumber) {
        if (isBlank(dateStr)) {
            throw new ValidationException("Missing dateOfBirth at line " + lineNumber);
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid dateOfBirth '" + dateStr +
                                          "' at line " + lineNumber);
        }
    }

    private BigDecimal parseRevenue(String revenueStr, int lineNumber) {
        if (isBlank(revenueStr)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(revenueStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid annualRevenue '" + revenueStr +
                                          "' at line " + lineNumber);
        }
    }

    private String deriveCustomerTier(BigDecimal revenue) {
        if (revenue.compareTo(new BigDecimal("1000000")) >= 0) return "GOLD";
        if (revenue.compareTo(new BigDecimal("100000")) >= 0) return "SILVER";
        return "BRONZE";
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        s = s.trim().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

### Item Writer (Bulk JDBC Insert)

```java
package com.rutik.systemdesign.spring.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Component
public class CustomerItemWriter implements ItemWriter<CustomerRecord> {

    private static final String UPSERT_SQL = """
        INSERT INTO customers
            (customer_id, first_name, last_name, email, date_of_birth,
             country_code, country_name, annual_revenue, customer_tier, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (customer_id) DO UPDATE SET
            first_name = EXCLUDED.first_name,
            last_name = EXCLUDED.last_name,
            email = EXCLUDED.email,
            date_of_birth = EXCLUDED.date_of_birth,
            country_code = EXCLUDED.country_code,
            country_name = EXCLUDED.country_name,
            annual_revenue = EXCLUDED.annual_revenue,
            customer_tier = EXCLUDED.customer_tier,
            updated_at = NOW()
        """;

    private final JdbcTemplate jdbcTemplate;

    public CustomerItemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends CustomerRecord> chunk) throws Exception {
        List<? extends CustomerRecord> items = chunk.getItems();

        // Uses PostgreSQL COPY-style batch insert — significantly faster than single INSERTs
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                CustomerRecord record = items.get(i);
                ps.setString(1, record.getCustomerId());
                ps.setString(2, record.getFirstName());
                ps.setString(3, record.getLastName());
                ps.setString(4, record.getEmail());
                ps.setDate(5, java.sql.Date.valueOf(record.getDateOfBirth()));
                ps.setString(6, record.getCountryCode());
                ps.setString(7, record.getCountryName());
                ps.setBigDecimal(8, record.getAnnualRevenue());
                ps.setString(9, record.getCustomerTier());
            }

            @Override
            public int getBatchSize() {
                return items.size();
            }
        });
    }
}
```

### Skip Listener — Writes Rejected Records to File

```java
package com.rutik.systemdesign.spring.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

@Component
public class CustomerSkipListener implements SkipListener<RawCustomerRecord, CustomerRecord> {

    private static final Logger log = LoggerFactory.getLogger(CustomerSkipListener.class);

    @Value("${batch.output.rejected-file:/data/customers/output/rejected.csv}")
    private String rejectedFilePath;

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped record during READ: {}", t.getMessage());
        writeRejected("READ_ERROR", "N/A", t.getMessage());
    }

    @Override
    public void onSkipInProcess(RawCustomerRecord item, Throwable t) {
        log.warn("Skipped record during PROCESS: customerId={} reason={}",
                 item.getCustomerId(), t.getMessage());
        writeRejected("PROCESS_ERROR", item.getCustomerId(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(CustomerRecord item, Throwable t) {
        log.warn("Skipped record during WRITE: customerId={} reason={}",
                 item.getCustomerId(), t.getMessage());
        writeRejected("WRITE_ERROR", item.getCustomerId(), t.getMessage());
    }

    private synchronized void writeRejected(String phase, String customerId, String reason) {
        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(rejectedFilePath, true)))) {
            writer.println(Instant.now() + "," + phase + "," + customerId + "," +
                           reason.replace(",", ";"));
        } catch (IOException e) {
            log.error("Failed to write to rejected file: {}", e.getMessage());
        }
    }
}
```

### Job Completion Listener (Metrics and Alerting)

```java
package com.rutik.systemdesign.spring.batch;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionListener.class);

    private final MeterRegistry meterRegistry;
    private final AlertService alertService;

    public JobCompletionListener(MeterRegistry meterRegistry, AlertService alertService) {
        this.meterRegistry = meterRegistry;
        this.alertService = alertService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting ETL job: jobId={} params={}",
                 jobExecution.getJobId(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long readCount = jobExecution.getStepExecutions().stream()
            .mapToLong(se -> se.getReadCount()).sum();
        long writeCount = jobExecution.getStepExecutions().stream()
            .mapToLong(se -> se.getWriteCount()).sum();
        long skipCount = jobExecution.getStepExecutions().stream()
            .mapToLong(se -> se.getSkipCount()).sum();

        Duration duration = Duration.between(
            jobExecution.getStartTime(), jobExecution.getEndTime());

        log.info("ETL job completed: status={} readCount={} writeCount={} skipCount={} durationSeconds={}",
                 jobExecution.getStatus(), readCount, writeCount, skipCount,
                 duration.toSeconds());

        // Record to Micrometer (Prometheus scrapes these)
        Counter.builder("batch.records.read")
               .tag("job", "customerEtlJob")
               .register(meterRegistry)
               .increment(readCount);

        Counter.builder("batch.records.written")
               .tag("job", "customerEtlJob")
               .register(meterRegistry)
               .increment(writeCount);

        Counter.builder("batch.records.skipped")
               .tag("job", "customerEtlJob")
               .register(meterRegistry)
               .increment(skipCount);

        Timer.builder("batch.job.duration")
             .tag("job", "customerEtlJob")
             .tag("status", jobExecution.getStatus().toString())
             .register(meterRegistry)
             .record(duration);

        // Alert on failure or excessive skip rate
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            alertService.sendAlert("ETL job FAILED after " + duration.toMinutes() + " minutes");
        } else if (readCount > 0 && (double) skipCount / readCount > 0.02) {
            alertService.sendAlert("ETL job skip rate exceeded 2%: skipped " + skipCount +
                                   " of " + readCount + " records");
        }
    }
}
```

### Job Scheduling and Launch

```java
package com.rutik.systemdesign.spring.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EtlJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(EtlJobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job customerEtlJob;

    public EtlJobScheduler(JobLauncher jobLauncher, Job customerEtlJob) {
        this.jobLauncher = jobLauncher;
        this.customerEtlJob = customerEtlJob;
    }

    @Scheduled(cron = "0 0 2 * * *")  // Every day at 2:00 AM
    public void runNightly() throws Exception {
        log.info("Triggering nightly ETL job");
        JobParameters params = new JobParametersBuilder()
            .addString("runDate", LocalDate.now().toString())
            // Including a unique parameter forces Spring Batch to treat each run as a new job instance
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(customerEtlJob, params);
    }
}
```

### Application Properties

```yaml
# application.yml
spring:
  batch:
    job:
      enabled: false  # Do not run jobs on startup — only on schedule
    jdbc:
      initialize-schema: always  # Creates batch_* tables if they don't exist

  datasource:
    url: jdbc:postgresql://localhost:5432/etldb
    username: etl_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20  # Enough for 8 partitions + overhead
      minimum-idle: 10
      connection-timeout: 30000

batch:
  input:
    directory: /data/customers/input
  output:
    rejected-file: /data/customers/output/rejected.csv
```

---

## Spring Components Used

| Spring Component | Purpose |
|---|---|
| `Job` / `JobBuilder` | Defines the ETL job with steps, incrementer, and listeners |
| `Step` / `StepBuilder` | Defines chunk-oriented processing with chunk size, skip policy, retry |
| `Partitioner` | Splits input files into `gridSize` independent execution contexts |
| `TaskExecutorPartitionHandler` | Runs worker steps on separate threads via `ThreadPoolTaskExecutor` |
| `FlatFileItemReader` | Reads CSV files line by line with configurable field mapping |
| `@StepScope` | Makes beans step-scoped so each partition gets its own reader with its own file path |
| `ItemProcessor` | Validates and transforms `RawCustomerRecord` to `CustomerRecord`; throws to trigger skip |
| `ItemWriter` | Bulk-writes to PostgreSQL using `JdbcTemplate.batchUpdate()` |
| `SkipListener` | Called when a record is skipped during read, process, or write — logs to rejected file |
| `JobExecutionListener` | Runs before and after the whole job — records Micrometer metrics, sends alerts |
| `JobRepository` | Persists step execution state enabling restart from last successful checkpoint |
| `JobLauncher` | Launches jobs with `JobParameters`; used by the `@Scheduled` trigger |
| `@Scheduled` | Triggers the job nightly via cron expression |
| `MeterRegistry` | Records read/write/skip counts and job duration as Micrometer metrics |

---

## Tradeoffs and Alternatives

### Chunk Size: 1,000 vs Larger/Smaller

| Chunk Size | TX Overhead | Memory Usage | Reprocessing on Retry | Lock Duration |
|---|---|---|---|---|
| 100 | High | Low | Low | Short |
| 1,000 (chosen) | Low | Medium | Medium | Medium |
| 10,000 | Very low | High | High | Long |

1,000 was chosen after benchmarking showed 1,000-row batches achieved 1,200 records/sec per thread, while 100-row batches achieved only 600 records/sec due to transaction overhead.

### In-Process vs Remote Partitioning

Remote partitioning (each partition runs on a separate JVM/pod) was considered but rejected because the 8-core server has sufficient capacity. Remote partitioning adds complexity: requires a message broker for step execution messages, separate worker deployments, and network coordination. It would be the correct choice if the data volume grew to 100 million records requiring horizontal scaling across multiple machines.

### JdbcBatchItemWriter vs CopyItemWriter (PostgreSQL COPY)

PostgreSQL's `COPY` command is 3–5x faster than batch `INSERT` for bulk loading because it bypasses SQL parsing and index maintenance during load. It was rejected here because `COPY` does not support `ON CONFLICT` (upsert) semantics — the ETL job may encounter records that already exist from a previous partial run. The upsert `INSERT ... ON CONFLICT DO UPDATE` is essential for idempotent reruns.

### FlatFileItemReader vs Spring Integration File Adapter

Spring Integration provides file polling and routing but adds significant complexity for a batch job that needs deterministic checkpointing. Spring Batch's `FlatFileItemReader` integrates natively with `JobRepository` for restart tracking. Spring Integration would be appropriate if the file processing needed to be reactive/streaming rather than batch-checkpoint-based.

---

## Interview Discussion Points

**Q: What happens when you restart a failed job — will records written by completed partitions be written again?**

A: No. Spring Batch stores each `StepExecution`'s status in the `JobRepository`. When the job is relaunched with the same `JobParameters` (same `runDate`), Spring Batch checks the `batch_step_execution` table. Partitions whose status is `COMPLETED` are skipped entirely. Only partitions in `FAILED` or `STARTED` status are reprocessed. The unique timestamp parameter ensures the same logical business run (`runDate`) maps to the same `JobInstance`, enabling restart behavior.

**Q: How do you prevent two instances of the cron job from running simultaneously?**

A: Spring Batch's `JobRepository` prevents concurrent execution of the same `JobInstance` by checking the status before launch — if an instance is already `STARTED`, launching it again throws `JobExecutionAlreadyRunningException`. For distributed deployments, use ShedLock or Spring's `@SchedulerLock` to ensure only one instance acquires the distributed lock and launches the job. The `JobRepository` database uniqueness constraint serves as a second line of defense.

**Q: The skip limit is set to 100,000 (1%). What happens if exactly 100,001 records are bad?**

A: Spring Batch throws a `SkipLimitExceededException` and fails the job with status `FAILED`. The `JobExecutionListener.afterJob()` detects the failure and fires the alert. The job can then be investigated, the skip limit temporarily increased, and the job restarted. Setting the skip limit at 1% is a data quality contract — exceeding it means the source data has a systemic problem that should not be silently ignored.

**Q: How do you handle a scenario where the CSV files arrive late (2 AM job starts but files aren't there yet)?**

A: The `CustomerPartitioner` throws `IllegalStateException` if no files are found, which fails the `masterStep` before any processing begins. The job status is `FAILED` and the alert fires. The operational procedure is: (1) fix the file delivery issue, (2) drop the files into the input directory, (3) relaunch the job. An enhanced version would use Spring Integration to poll for files and trigger the job only when all expected files have arrived, with a configurable timeout.

**Q: How do you tune the number of partitions to maximize throughput on an 8-core machine?**

A: Set `gridSize = number of available cores`. With 8 cores and 8 partitions, all cores are fully utilized. However, the bottleneck is often the database writer rather than CPU. Profile the job: if CPU usage is below 50% but database write latency is high, the bottleneck is the database — increase the chunk size to reduce transaction overhead rather than adding more partitions. If CPU is pegged at 100% and the processor is the bottleneck, the `ItemProcessor` logic is the constraint — optimize it (e.g., reduce regex compilation by pre-compiling patterns as static finals).

**Q: How would you implement a progress API so operators can check how far along the job is?**

A: Expose an actuator endpoint that queries the `JobRepository` for the current `JobExecution` and aggregates read/write counts across all `StepExecution`s. Spring Batch Actuator (`spring-boot-actuator` + `spring-batch`) provides `/actuator/batch/jobs` and `/actuator/batch/jobs/{jobName}/{jobInstanceId}` endpoints out of the box in Spring Boot 3.x. For a real-time dashboard, push the step execution metrics to a Micrometer gauge that Prometheus scrapes every 15 seconds, and display in Grafana.

---

## Failure Scenarios and Recovery

Batch jobs run unattended overnight, so the defining question is what happens when a job dies at record 750,000 of a million. Spring Batch's `JobRepository` is the durable state machine that makes restart-from-failure possible.

### Failure: Job Dies Mid-Chunk

Spring Batch commits at chunk boundaries. With a chunk size of 1,000, after processing 750 chunks the `JobRepository` has recorded 750,000 read/write counts and the read cursor position for each step. If the process is killed at chunk 751, that in-flight chunk's transaction rolls back (its 1,000 items are not committed), and the `StepExecution` is left in a `STARTED`/`FAILED` state with the last committed read count at 750,000.

```
JobRepository state after a crash at chunk 751:

  BATCH_JOB_EXECUTION:   status=FAILED, exit_code=FAILED
  BATCH_STEP_EXECUTION:  read_count=750000, write_count=750000,
                         commit_count=750, status=FAILED
  -> last committed chunk = 750; chunk 751 rolled back cleanly
```

On restart, Spring Batch finds the existing `JobInstance` (same identifying job parameters), sees the `FAILED` execution, and resumes the step from read offset 750,000 — it does NOT reprocess the first 750,000 records. This requires the step to be restartable and the reader to be restartable (e.g., `FlatFileItemReader` persists `read.count` in the `ExecutionContext`).

```java
// FIX: ensure the job/step is restartable and parameters are identifying
@Bean
public Job importJob(JobRepository repo, Step importStep) {
    return new JobBuilder("importJob", repo)
        .preventRestart()  // BROKEN if uncommented: forbids restart-from-failure
        .start(importStep)
        .build();
}

// Correct version: do NOT call preventRestart(); leave the step restartable (default true)
@Bean
public Step importStep(JobRepository repo, PlatformTransactionManager tx,
                       ItemReader<Record> reader, ItemWriter<Record> writer) {
    return new StepBuilder("importStep", repo)
        .<Record, Record>chunk(1000, tx)
        .reader(reader)        // FlatFileItemReader is restartable: saves read.count
        .processor(processor())
        .writer(writer)
        .faultTolerant()
        .skipLimit(100_000)
        .skip(FlatFileParseException.class)
        .build();
}
```

Recovery procedure:
1. Investigate the failure cause (disk full, downstream DB outage, bad record beyond skip limit).
2. Fix the root cause.
3. Relaunch the job with the SAME identifying job parameters (e.g., `run.date=2026-05-24`). Spring Batch resumes from the last committed chunk automatically.
4. Time-to-recovery: only the un-processed remainder is reprocessed. A crash at 75% means re-running ~25% of the work, not 100%.

Note: identifying parameters matter. If you launch with a new `run.id` timestamp every time (a non-identifying-vs-identifying mistake), Spring Batch creates a brand-new `JobInstance` and reprocesses from zero. Use a `JobParametersIncrementer` only for scheduled fresh runs, never for restarts.

### Failure: Writer Succeeds but Process Crashes Before Commit Metadata Persists

The chunk transaction and the `JobRepository` metadata update participate in the same transaction (when sharing a `PlatformTransactionManager`), so they commit atomically. If they used separate transaction managers, a crash between the data write and the metadata write would double-process the chunk on restart. Fix: keep the business data and batch metadata on the same `DataSource`/transaction manager so chunk commit and bookkeeping are atomic.

---

## Capacity Planning

### Chunk and Throughput Math

```
Total records:    1,000,000
Chunk size:       1,000
Chunks:           1,000,000 / 1,000 = 1,000 chunks
Per-chunk time:   200ms (read + process + write 1,000 items)

Single-threaded:  1,000 chunks x 200ms = 200,000ms = 200 seconds
```

### Parallelism with Partitioning

```
TaskExecutorPartitioner across 8 threads:
  1,000 chunks / 8 threads = 125 chunks/thread
  125 x 200ms = 25,000ms = 25 seconds (8x speedup, ideal case)

Real-world: ~6-7x speedup due to DB write contention and uneven partitions.
```

```java
// Partitioned step: master fans out to 8 worker partitions
@Bean
public Step masterStep(JobRepository repo, Step workerStep, Partitioner partitioner) {
    return new StepBuilder("masterStep", repo)
        .partitioner("workerStep", partitioner)
        .step(workerStep)
        .gridSize(8)                       // 8 partitions
        .taskExecutor(partitionTaskExecutor())
        .build();
}

@Bean
public ThreadPoolTaskExecutor partitionTaskExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(8);               // one thread per partition
    exec.setMaxPoolSize(8);
    exec.setQueueCapacity(0);              // no queuing; partitions run immediately
    exec.setThreadNamePrefix("batch-part-");
    return exec;
}
```

### Connection Pool and Memory Math

```
Writer connections:   8 partition threads each hold 1 DB connection while writing
                      -> HikariCP pool must be >= 8 + 2 (Spring Batch metadata) = 10
JDBC batch insert:    chunk 1,000 -> one batched INSERT of 1,000 rows per commit
                      reduces round-trips from 1,000 to 1.

Heap per partition:   chunk 1,000 x ~2 KB/item = ~2 MB live per partition
                      8 partitions x 2 MB = 16 MB chunk working set (negligible)
Reader buffering:     FlatFileItemReader streams line-by-line, O(1) memory.
                      DO NOT load the whole file into a List.
```

Sizing rule: HikariCP pool size must be at least `gridSize + metadata_connections`. With 8 partitions, set pool to 10–12. A pool smaller than `gridSize` causes partition threads to block waiting for connections, silently serializing the "parallel" job.

---

## Additional Production War Stories

### War Story 1: Stateful ItemProcessor Leaked Data Across Chunks

An `ItemProcessor` accumulated a running total in an instance field to compute aggregates. Because the processor bean was a singleton shared across chunks (and across partition threads), state from chunk N bled into chunk N+1, and under partitioning two threads corrupted each other's totals. Records were enriched with stale data from a previous chunk.

```java
// BROKEN: instance field holds state across chunks and across partition threads
@Component
public class EnrichingProcessor implements ItemProcessor<Record, Record> {
    private BigDecimal runningTotal = BigDecimal.ZERO; // shared mutable state!

    @Override
    public Record process(Record item) {
        runningTotal = runningTotal.add(item.getAmount()); // leaks across chunks
        item.setRunningTotal(runningTotal);                // stale/corrupted under parallelism
        return item;
    }
}
```

```java
// FIX 1: make the processor stateless — never hold cross-item state in a field
@Component
public class EnrichingProcessor implements ItemProcessor<Record, Record> {
    @Override
    public Record process(Record item) {
        item.setCategory(classify(item)); // pure function of the input item only
        return item;
    }
}
```

```java
// FIX 2: if per-run state is genuinely needed, scope the bean to the step
@Bean
@StepScope   // a fresh processor instance per step execution (per partition)
public ItemProcessor<Record, Record> scopedProcessor(
        @Value("#{stepExecutionContext['partitionKey']}") String key) {
    return new PartitionLocalProcessor(key);
}
```

The rule: an `ItemProcessor` must be stateless, or `@StepScope`d when it must hold per-execution state. Running aggregates belong in a separate aggregation step or in the database, never in a shared processor field.

### War Story 2: JobParametersIncrementer on Restart Reprocessed Everything

The job used a `RunIdIncrementer` so every launch got a unique `run.id`. When an operator relaunched a failed job, the new `run.id` created a brand-new `JobInstance`, so Spring Batch did not see it as a restart and reprocessed all 1,000,000 records from zero, double-writing every already-committed row.

```java
// BROKEN: every launch is a new JobInstance -> restart reprocesses from zero
jobLauncher.run(importJob,
    new JobParametersBuilder()
        .addLong("run.id", System.currentTimeMillis()) // always unique
        .toJobParameters());
```

```java
// FIX: use a stable identifying parameter so a restart matches the failed instance
jobLauncher.run(importJob,
    new JobParametersBuilder()
        .addString("run.date", "2026-05-24")  // identifying + stable for the day
        .toJobParameters());
// Relaunching with the same run.date resumes the FAILED instance from chunk 751.
```

For scheduled fresh runs use an incrementer; for operator restarts of a failed run, relaunch with the original identifying parameters.

---

## @JobScope and @StepScope Bean Lifecycle

Spring Batch defines two custom scopes that defer bean creation until the job or step is actually running, which is what enables late binding of runtime parameters.

| Scope        | Bean created when            | Destroyed when         | Use for                                                      |
|--------------|------------------------------|------------------------|-------------------------------------------------------------|
| `@JobScope`  | the job execution starts     | the job execution ends | beans needing job parameters (`#{jobParameters['run.date']}`) |
| `@StepScope` | the step execution starts    | the step execution ends| readers/writers/processors needing step context, partition keys |
| singleton    | application context refresh  | context shutdown       | stateless shared components only                             |

Why this matters: `@StepScope` beans are created fresh per step execution, and under partitioning, per partition step execution. This is exactly why a `@StepScope` processor solves the stateful-processor bug — each partition gets its own instance with no shared mutable state. It also enables late binding: a reader can inject `#{jobParameters['inputFile']}` or `#{stepExecutionContext['partitionKey']}`, which do not exist at context-refresh time.

```java
// Late binding only works because @StepScope defers creation until the step runs
@Bean
@StepScope
public FlatFileItemReader<Record> reader(
        @Value("#{jobParameters['inputFile']}") String path,          // job-level param
        @Value("#{stepExecutionContext['fileName']}") String partFile) { // partition param
    return new FlatFileItemReaderBuilder<Record>()
        .name("recordReader")
        .resource(new FileSystemResource(partFile != null ? partFile : path))
        .delimited()
        .names("id", "amount", "category")
        .targetType(Record.class)
        .build();
}
```

A common pitfall: injecting a `@StepScope` bean into a singleton without a scoped proxy throws `BeanCreationException` because the step scope is not active at singleton instantiation time. Spring Batch wires a scoped proxy automatically for these beans, but custom configurations must mark `proxyMode = ScopedProxyMode.TARGET_CLASS` if defining the scope manually.

---

## Multi-Region Considerations

Batch pipelines are usually region-pinned to where the data lives, because moving terabytes across regions for processing is slow and expensive. The design goal is to process data in its home region and only aggregate results globally.

```
   Region US (us-east-1)                      Region EU (eu-west-1)
   +--------------------------+               +--------------------------+
   | S3 us: input files       |               | S3 eu: input files       |
   |        |                 |               |        |                 |
   | [ Batch job US ]         |               | [ Batch job EU ]         |
   |   JobRepository (US DB)  |               |   JobRepository (EU DB)  |
   |        |                 |               |        |                 |
   | results -> US warehouse  |               | results -> EU warehouse  |
   +-----------+--------------+               +-----------+--------------+
               |                                          |
               +-------------- nightly ETL --------------+
                                  |
                       [ Global analytics warehouse ]
```

Design changes for multi-region:
- Run an independent batch job per region against region-local input storage and a region-local `JobRepository`. Each region's `JobRepository` is its own state machine; restart semantics never cross regions.
- Data residency: EU input data is processed by the EU job and written to EU storage; it never transits to a US compute node. This keeps regulated data in-region.
- Global aggregates are produced by a downstream cross-region ETL into a central analytics warehouse, run on already-aggregated, residency-cleared outputs — not raw records.
- Scheduling is region-local (each region's scheduler with its own ShedLock/`@SchedulerLock` distributed lock), so a region outage does not block other regions' nightly runs.
- A region that fails its nightly run is restarted independently from its own `JobRepository` once the region recovers; other regions are unaffected.

---

## Additional Interview Questions

**Q: How does Spring Batch resume a job that crashed at chunk 751 of 1,000?**

A: Spring Batch commits at chunk boundaries and records progress in the `JobRepository`. After 750 committed chunks, the `StepExecution` shows `read_count=750000` and `commit_count=750`; the in-flight chunk 751 rolls back cleanly because its transaction never committed. On relaunch with the same identifying job parameters, Spring Batch finds the existing `JobInstance` in `FAILED` state and resumes the restartable step from read offset 750,000, reprocessing only the remaining ~25% rather than starting over. This depends on the reader being restartable (e.g., `FlatFileItemReader` persists `read.count` in the `ExecutionContext`) and on not calling `preventRestart()`.

**Q: Why must an ItemProcessor be stateless, and what if it genuinely needs per-run state?**

A: An `ItemProcessor` bean is a singleton shared across all chunks and, under partitioning, across all partition threads. Any mutable instance field leaks state from one chunk or thread into another, producing stale or corrupted output and race conditions. The processor must therefore be a pure function of its input item. If per-execution state is genuinely required, scope the bean with `@StepScope` so each step (and each partition) gets a fresh instance, or move running aggregates into a dedicated aggregation step or the database where they are computed correctly.

**Q: How do you tune chunk size and partition count to process a million records fastest?**

A: First compute the single-threaded baseline: 1,000,000 records at chunk size 1,000 is 1,000 chunks; at 200ms per chunk that is 200 seconds. Then partition: with a `TaskExecutorPartitioner` across 8 threads, each handles 125 chunks for an ideal ~25 seconds, realistically 6–7x due to DB write contention. Set `gridSize` to the core count and ensure the HikariCP pool is at least `gridSize + metadata connections` (10–12 for 8 partitions), otherwise partition threads block on connections and silently serialize. Larger chunk sizes reduce commit overhead but increase rollback cost and memory; tune by profiling whether the bottleneck is CPU (processor) or the database writer.

**Q: What is the difference between @JobScope and @StepScope?**

A: Both are Spring Batch custom scopes that defer bean creation until runtime to enable late binding of parameters. `@JobScope` beans are created when the job execution starts and destroyed when it ends, used for beans needing job parameters. `@StepScope` beans are created when the step execution starts and destroyed when it ends — and crucially, under partitioning a fresh instance is created per partition step execution, which is what makes a `@StepScope` reader or processor safe under parallelism. Late binding like `#{jobParameters['inputFile']}` or `#{stepExecutionContext['partitionKey']}` works only because these scopes defer instantiation past context-refresh time.

**Q: Why can launching a job with a unique run.id each time break restart, and how do you fix it?**

A: Spring Batch identifies a `JobInstance` by its identifying job parameters. If every launch injects a unique `run.id` (e.g., `System.currentTimeMillis()`), each launch is a brand-new `JobInstance`, so relaunching a failed job is not recognized as a restart — it reprocesses all records from zero and double-writes already-committed rows. The fix is to relaunch a failed run with the original stable identifying parameter (e.g., `run.date=2026-05-24`) so Spring Batch matches the failed instance and resumes from the last committed chunk. Use a `JobParametersIncrementer` only for scheduled fresh runs, never for operator restarts.
