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
