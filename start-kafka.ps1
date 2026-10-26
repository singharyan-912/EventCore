Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Starting Apache Kafka on localhost:9092 (in-process)..." -ForegroundColor Cyan
Write-Host " Press Ctrl+C to stop Kafka at any time." -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

.\mvnw.cmd test-compile exec:java "-Dexec.mainClass=com.eventprocessor.reliable_event_processor.dev.KafkaDevServer" "-Dexec.classpathScope=test"
