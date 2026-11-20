$ErrorActionPreference = "Stop"

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = "E:\Android Studio\Sdk"
$buildTools = Join-Path $sdk "build-tools\30.0.3"
$androidJar = Join-Path $sdk "platforms\android-30\android.jar"

$aapt2 = Join-Path $buildTools "aapt2.exe"
$aapt = Join-Path $buildTools "aapt.exe"
$zipalign = Join-Path $buildTools "zipalign.exe"

$javaHome = Split-Path -Parent (Split-Path -Parent (Get-Command java).Source)
$java = Join-Path $javaHome "bin\java.exe"
$javac = Join-Path $javaHome "bin\javac.exe"
$jar = Join-Path $javaHome "bin\jar.exe"
$keytool = Join-Path $javaHome "bin\keytool.exe"
$d8Jar = (Get-ChildItem $buildTools -Recurse -Filter d8.jar | Select-Object -First 1).FullName
$apksignerJar = (Get-ChildItem $buildTools -Recurse -Filter apksigner.jar | Select-Object -First 1).FullName

if (!(Test-Path $androidJar)) { throw "找不到 android.jar: $androidJar" }
if (!(Test-Path $aapt2)) { throw "找不到 aapt2: $aapt2" }
if (!$d8Jar) { throw "找不到 d8.jar" }
if (!$apksignerJar) { throw "找不到 apksigner.jar" }

# aapt2 无法处理中文路径，先复制到纯英文临时目录再编译
$tempRoot = $env:TEMP
if ([string]::IsNullOrEmpty($tempRoot)) {
    $tempRoot = "C:\Users\Administrator\AppData\Local\Temp"
}
$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
$stage = Join-Path $tempRoot ("sodaBuild_" + $suffix)
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item -Recurse -Force (Join-Path $project "src") $stage
Copy-Item -Recurse -Force (Join-Path $project "res") $stage
Copy-Item -Force (Join-Path $project "AndroidManifest.xml") $stage

$buildDir = Join-Path $stage "build"
$distDir = Join-Path $stage "dist"
$genDir = Join-Path $buildDir "gen"
$classesDir = Join-Path $buildDir "classes"
$dexDir = Join-Path $buildDir "dex"
$resZip = Join-Path $buildDir "resources.zip"
$classesJar = Join-Path $buildDir "classes.jar"
$unsigned = Join-Path $buildDir "app-unsigned.apk"
$aligned = Join-Path $buildDir "app-aligned.apk"
$signed = Join-Path $distDir "sodaHelper.apk"
$keystore = Join-Path $stage "soda.keystore"

if (Test-Path (Join-Path $project "soda.keystore")) {
    Copy-Item -Force (Join-Path $project "soda.keystore") $keystore
}

New-Item -ItemType Directory -Force -Path $genDir, $classesDir, $dexDir, $distDir | Out-Null

function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "命令失败($LASTEXITCODE): $exe $($arguments -join ' ')"
    }
}

Write-Output "1/8 准备图标"
$iconDir = Join-Path $stage "res\mipmap-xxxhdpi"
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
$iconPath = Join-Path $iconDir "ic_launcher.png"
if (!(Test-Path $iconPath)) {
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList 192, 192
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::FromArgb(15, 220, 120))
    $font = New-Object System.Drawing.Font -ArgumentList "Arial", 96, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $brush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::White)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rect = New-Object System.Drawing.RectangleF -ArgumentList 0, 0, 192, 192
    $graphics.DrawString("S", $font, $brush, $rect, $format)
    $bmp.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bmp.Dispose()
    $font.Dispose()
    $brush.Dispose()
}

Write-Output "2/8 编译资源"
Run $aapt2 @("compile", "--dir", (Join-Path $stage "res"), "-o", $resZip)

Write-Output "3/8 链接资源"
Run $aapt2 @(
    "link", "-o", $unsigned,
    "-I", $androidJar,
    "--manifest", (Join-Path $stage "AndroidManifest.xml"),
    "-R", $resZip,
    "--java", $genDir,
    "--min-sdk-version", "24",
    "--target-sdk-version", "30",
    "--version-code", "1",
    "--version-name", "0.1.0",
    "--auto-add-overlay"
)

Write-Output "4/8 编译 Java"
$sources = @()
Get-ChildItem -Path (Join-Path $stage "src") -Recurse -Filter *.java | ForEach-Object { $sources += $_.FullName }
Get-ChildItem -Path $genDir -Recurse -Filter R.java | ForEach-Object { $sources += $_.FullName }
$javacArgs = @(
    "-encoding", "UTF-8",
    "-source", "8",
    "-target", "8",
    "-classpath", $androidJar,
    "-d", $classesDir
) + $sources
Run $javac $javacArgs

Write-Output "5/8 生成 dex"
Run $jar @("cf", $classesJar, "-C", $classesDir, ".")
Run $java @("-cp", $d8Jar, "com.android.tools.r8.D8",
    "--lib", $androidJar, "--min-api", "24", "--output", $dexDir, $classesJar)

Push-Location $dexDir
try {
    Run $aapt @("add", $unsigned, "classes.dex")
} finally {
    Pop-Location
}

Write-Output "6/8 对齐"
Run $zipalign @("-f", "4", $unsigned, $aligned)

Write-Output "7/8 准备签名"
if (!(Test-Path $keystore)) {
    Run $keytool @(
        "-genkeypair", "-v",
        "-keystore", $keystore,
        "-alias", "soda",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-storepass", "soda123",
        "-keypass", "soda123",
        "-dname", "CN=SodaHelper, OU=Local, O=Local, L=Local, ST=Local, C=CN"
    )
}

Write-Output "8/8 签名"
Run $java @("-jar", $apksignerJar, "sign",
    "--ks", $keystore,
    "--ks-key-alias", "soda",
    "--ks-pass", "pass:soda123",
    "--key-pass", "pass:soda123",
    "--out", $signed,
    $aligned)

New-Item -ItemType Directory -Force -Path (Join-Path $project "dist") | Out-Null
Copy-Item -Force $signed (Join-Path $project "dist\sodaHelper.apk")
if (!(Test-Path (Join-Path $project "soda.keystore"))) {
    Copy-Item -Force $keystore (Join-Path $project "soda.keystore")
}
if (!(Test-Path (Join-Path $project "res\mipmap-xxxhdpi\ic_launcher.png"))) {
    New-Item -ItemType Directory -Force -Path (Join-Path $project "res\mipmap-xxxhdpi") | Out-Null
    Copy-Item -Force $iconPath (Join-Path $project "res\mipmap-xxxhdpi\ic_launcher.png")
}

Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
Write-Output ""
Write-Output "APK 已生成: $(Join-Path $project 'dist\sodaHelper.apk')"
