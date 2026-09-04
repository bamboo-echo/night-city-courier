# 夜之城快递员 - 一键构建脚本 (Windows PowerShell)
# 用法:  cd NightCityCourier ; .\build.ps1
# 可选:  .\build.ps1 -Run      编译后直接启动游戏
#        .\build.ps1 -Test     编译并运行单元测试

param(
    [switch]$Run,
    [switch]$Test
)

$ErrorActionPreference = "Stop"
$ProjectDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$SrcDir      = Join-Path $ProjectDir "src\com\moji\NightCityCourier"
$OutDir      = Join-Path $ProjectDir "out"
$TestSrcDir  = Join-Path $ProjectDir "test\com\moji\NightCityCourier"
$TestOutDir  = Join-Path $ProjectDir "test-out"

# ── 依赖检查 ──
foreach ($tool in @("javac", "java")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host "[错误] 未找到 $tool，请先安装 JDK 17 或更高版本。" -ForegroundColor Red
        exit 1
    }
}

$version = & javac -version 2>&1 | ForEach-Object { ($_ | Out-String).Trim() }
Write-Host "[环境] $version"

# ── 编译 ──
if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "[编译] 主程序..." -ForegroundColor Cyan
javac -encoding UTF-8 -d $OutDir (Join-Path $SrcDir "*.java")
if ($LASTEXITCODE -ne 0) { Write-Host "[错误] 编译失败" -ForegroundColor Red; exit 1 }

# ── 测试 ──
if ($Test) {
    if (Test-Path $TestOutDir) { Remove-Item -Recurse -Force $TestOutDir }
    New-Item -ItemType Directory -Force -Path $TestOutDir | Out-Null
    Write-Host "[编译] 单元测试..." -ForegroundColor Cyan
    javac -encoding UTF-8 -d $TestOutDir (Join-Path $SrcDir "*.java") (Join-Path $TestSrcDir "*.java")
    if ($LASTEXITCODE -ne 0) { Write-Host "[错误] 测试编译失败" -ForegroundColor Red; exit 1 }

    Write-Host "[运行] 单元测试..." -ForegroundColor Cyan
    java -cp $TestOutDir com.moji.NightCityCourier.CoreSystemsTest
    if ($LASTEXITCODE -ne 0) { Write-Host "[失败] 单元测试未全部通过" -ForegroundColor Red; exit 1 }
    Write-Host "[完成] 单元测试全部通过 ✅" -ForegroundColor Green
}

Write-Host "[完成] 编译产物位于 out\ 目录" -ForegroundColor Green

if ($Run) {
    Write-Host "[启动] 游戏..." -ForegroundColor Cyan
    java -cp $OutDir com.moji.NightCityCourier.Main
}
