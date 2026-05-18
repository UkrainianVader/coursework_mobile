param(
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug"
)

$task = if ($BuildType -eq "release") { "assembleRelease" } else { "assembleDebug" }

Push-Location "$PSScriptRoot\.."
try {
    if (Test-Path ".\gradlew.bat") {
        .\gradlew.bat $task
    }
    elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
        gradle $task
    }
    else {
        Write-Error "Gradle is not installed and gradlew.bat is missing. Install Gradle or generate wrapper from Android Studio."
        exit 1
    }
}
finally {
    Pop-Location
}
