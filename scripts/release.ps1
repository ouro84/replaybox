<#
.SYNOPSIS
  Builds and publishes a tagged Replaybox release (local CI, see SPEC.md "Releases").

.DESCRIPTION
  1. Reads the version from gradle.properties (replaybox.version=X.Y.Z).
  2. Checks: on main, clean tree, in sync with origin/main, tag vX.Y.Z absent.
  3. Runs testDebugUnitTest assembleDebug assembleRelease (JDK 11).
  4. Copies the APKs to build/release/replaybox-X.Y.Z-{debug,release}.apk.
  5. Unless -DryRun: tags vX.Y.Z, pushes the tag, creates the GitHub Release
     with both APKs attached (gh CLI, notes generated from merged PRs).

  Both APKs are signed with the debug keystore of this machine
  (~/.android/debug.keystore) - the certificate already on the box.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File scripts\release.ps1
#>
param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail([string]$message) {
    Write-Host "release: $message" -ForegroundColor Red
    exit 1
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jre'
}

$line = Get-Content (Join-Path $root 'gradle.properties') | Where-Object { $_ -match '^replaybox\.version=' }
if (-not $line) { Fail 'replaybox.version missing from gradle.properties' }
$version = ($line -split '=', 2)[1].Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { Fail "replaybox.version '$version' is not X.Y.Z" }
$tag = "v$version"
Write-Host "release: version $version (tag $tag)"

if (-not $DryRun) {
    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($branch -ne 'main') { Fail "must run on main, current branch is $branch" }
    if (git status --porcelain) { Fail 'working tree is not clean' }
    git fetch --quiet origin main --tags
    if ($LASTEXITCODE -ne 0) { Fail 'git fetch failed' }
    if ((git rev-parse HEAD).Trim() -ne (git rev-parse origin/main).Trim()) { Fail 'main is not in sync with origin/main' }
    if (git tag --list $tag) { Fail "tag $tag already exists - bump replaybox.version first" }
}

& (Join-Path $root 'gradlew.bat') --no-daemon testDebugUnitTest assembleDebug assembleRelease
if ($LASTEXITCODE -ne 0) { Fail 'gradle build or tests failed' }

$out = Join-Path $root 'build\release'
New-Item -ItemType Directory -Force $out | Out-Null
$debugApk = Join-Path $out "replaybox-$version-debug.apk"
$releaseApk = Join-Path $out "replaybox-$version-release.apk"
Copy-Item (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') $debugApk -Force
Copy-Item (Join-Path $root 'app\build\outputs\apk\release\app-release.apk') $releaseApk -Force
Write-Host "release: built $debugApk"
Write-Host "release: built $releaseApk"

if ($DryRun) {
    Write-Host 'release: dry run - no tag, no GitHub Release'
    exit 0
}

git tag -a $tag -m "Replaybox $version"
if ($LASTEXITCODE -ne 0) { Fail 'git tag failed' }
git push origin $tag
if ($LASTEXITCODE -ne 0) { Fail 'git push of the tag failed' }

gh release create $tag $releaseApk $debugApk --title "Replaybox $version" --generate-notes --latest
if ($LASTEXITCODE -ne 0) { Fail 'gh release create failed (tag is pushed: rerun gh release create by hand)' }
Write-Host "release: published $tag"
