# Script to fix R class issues in Android Studio
Write-Host "Cleaning Android project to fix R class issues..." -ForegroundColor Yellow

# Delete build folders
if (Test-Path "app\build") {
    Remove-Item -Path "app\build" -Recurse -Force
    Write-Host "Deleted app\build folder" -ForegroundColor Green
}

if (Test-Path "build") {
    Remove-Item -Path "build" -Recurse -Force
    Write-Host "Deleted build folder" -ForegroundColor Green
}

# Delete .gradle cache
if (Test-Path ".gradle") {
    Remove-Item -Path ".gradle" -Recurse -Force
    Write-Host "Deleted .gradle folder" -ForegroundColor Green
}

Write-Host "`nCleanup complete!" -ForegroundColor Green
Write-Host "`nNext steps in Android Studio:" -ForegroundColor Cyan
Write-Host "1. File -> Invalidate Caches -> Invalidate and Restart" -ForegroundColor White
Write-Host "2. File -> Sync Project with Gradle Files" -ForegroundColor White
Write-Host "3. Build -> Clean Project" -ForegroundColor White
Write-Host "4. Build -> Rebuild Project" -ForegroundColor White



