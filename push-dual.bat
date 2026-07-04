@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM Git 一键提交并同时推送到 Gitee + GitHub
REM 用法：
REM 1. 把本文件放到项目根目录
REM 2. 修改下面 GITEE_URL / GITHUB_URL
REM 3. 双击运行，或命令行执行：push-dual.bat "提交说明"
REM ============================================================

REM ===================== 你只需要改这里 =====================

set "https://gitee.com/yunox/uploadTool.git"

REM 远程仓库名称，一般不用改
set "GITEE_REMOTE=gitee"
set "GITHUB_REMOTE=github"

REM 新仓库默认分支名：main 或 master
set "DEFAULT_BRANCH=master"

REM 是否推送 tags：1=推送，0=不推送
set "PUSH_TAGS=0"

REM 是否允许脚本在非 Git 仓库中自动 git init：1=允许，0=不允许
set "AUTO_INIT=1"

REM ============================================================

echo.
echo ============================================================
echo Git 双平台一键推送脚本
echo ============================================================

cd /d "%~dp0"

echo 当前目录：%cd%

echo.
echo ==============================
echo [1/8] 检查 Git 环境
echo ==============================

git --version >nul 2>nul
if errorlevel 1 (
echo [ERROR] 未检测到 Git，请先安装 Git 并配置环境变量。
pause
exit /b 1
)

if not exist ".git" (
if "%AUTO_INIT%"=="1" (
echo 当前目录不是 Git 仓库，开始执行 git init...
git init
if errorlevel 1 (
echo [ERROR] git init 失败。
pause
exit /b 1
)
) else (
echo [ERROR] 当前目录不是 Git 仓库。
pause
exit /b 1
)
)

echo.
echo ==============================
echo [2/8] 确认当前分支
echo ==============================

set "CURRENT_BRANCH="

for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD 2^>nul') do set "CURRENT_BRANCH=%%B"

if "%CURRENT_BRANCH%"=="HEAD" set "CURRENT_BRANCH="

if not defined CURRENT_BRANCH (
echo 当前无有效分支，切换/创建默认分支：%DEFAULT_BRANCH%
git checkout -B %DEFAULT_BRANCH% >nul 2>nul
set "CURRENT_BRANCH=%DEFAULT_BRANCH%"
)

echo 当前分支：%CURRENT_BRANCH%

echo.
echo ==============================
echo [3/8] 配置远程仓库
echo ==============================

if "%GITEE_URL%"=="https://gitee.com/你的用户名/你的仓库名.git" (
echo [ERROR] 请先修改脚本顶部的 GITEE_URL。
pause
exit /b 1
)

if "%GITHUB_URL%"=="[git@github.com](mailto:git@github.com):你的用户名/你的仓库名.git" (
echo [ERROR] 请先修改脚本顶部的 GITHUB_URL。
pause
exit /b 1
)

git remote get-url %GITEE_REMOTE% >nul 2>nul
if errorlevel 1 (
echo 添加 Gitee remote：%GITEE_REMOTE%
git remote add %GITEE_REMOTE% "%GITEE_URL%"
) else (
echo 更新 Gitee remote：%GITEE_REMOTE%
git remote set-url %GITEE_REMOTE% "%GITEE_URL%"
)

if errorlevel 1 (
echo [ERROR] 配置 Gitee remote 失败。
pause
exit /b 1
)

git remote get-url %GITHUB_REMOTE% >nul 2>nul
if errorlevel 1 (
echo 添加 GitHub remote：%GITHUB_REMOTE%
git remote add %GITHUB_REMOTE% "%GITHUB_URL%"
) else (
echo 更新 GitHub remote：%GITHUB_REMOTE%
git remote set-url %GITHUB_REMOTE% "%GITHUB_URL%"
)

if errorlevel 1 (
echo [ERROR] 配置 GitHub remote 失败。
pause
exit /b 1
)

echo.
echo 当前 remote：
git remote -v

echo.
echo ==============================
echo [4/8] 检查 .gitignore
echo ==============================

if not exist ".gitignore" (
echo 未发现 .gitignore，创建基础版本...
(
echo # dependencies
echo node_modules/
echo.
echo # build output
echo dist/
echo target/
echo.
echo # logs
echo logs/
echo *.log
echo.
echo # IDE
echo .idea/
echo .vscode/
echo *.iml
echo.
echo # system
echo .DS_Store
echo Thumbs.db
echo.
echo # runtime sensitive files
echo runtime.core
echo runtime.window
echo runtime.state
echo private_pkcs8.pem
echo *.pem
echo.
echo # env local
echo .env.local
echo .env.*.local
) > .gitignore
echo 已创建基础 .gitignore。
) else (
echo 已存在 .gitignore，跳过创建。
)

echo.
echo ==============================
echo [5/8] 显示当前变更
echo ==============================

git status --short

echo.
echo [安全提醒]
echo 推送前请确认不要提交：
echo - 数据库密码、Redis 密码、AccessKey、AppSecret
echo - application.yml 中的生产敏感配置
echo - private_pkcs8.pem、runtime.core、runtime.window、runtime.state
echo - target、dist、node_modules、logs
echo.

set /p CONFIRM=确认要 add、commit 并推送到 Gitee + GitHub 吗？输入 y 继续：
if /i not "%CONFIRM%"=="y" (
echo 已取消。
pause
exit /b 0
)

echo.
echo ==============================
echo [6/8] Git add + commit
echo ==============================

git add .
if errorlevel 1 (
echo [ERROR] git add 失败。
pause
exit /b 1
)

git diff --cached --quiet
if errorlevel 1 (
set "COMMIT_MSG=%~1"

```
if not defined COMMIT_MSG (
    set /p COMMIT_MSG=请输入提交说明：
)

if not defined COMMIT_MSG (
    set "COMMIT_MSG=update project"
)

echo 提交说明：!COMMIT_MSG!
git commit -m "!COMMIT_MSG!"

if errorlevel 1 (
    echo [ERROR] git commit 失败。
    pause
    exit /b 1
)
```

) else (
echo 没有需要提交的新变更，跳过 commit。
)

echo.
echo ==============================
echo [7/8] 推送到 Gitee
echo ==============================

git push -u %GITEE_REMOTE% %CURRENT_BRANCH%
set "GITEE_RESULT=%ERRORLEVEL%"

if "%PUSH_TAGS%"=="1" (
git push %GITEE_REMOTE% --tags
)

echo.
echo ==============================
echo [8/8] 推送到 GitHub
echo ==============================

)

echo.
echo ============================================================
echo 推送结果
echo ============================================================

if "%GITEE_RESULT%"=="0" (
echo [OK] Gitee 推送成功
) else (
echo [FAIL] Gitee 推送失败
)

if "%GITHUB_RESULT%"=="0" (
echo [OK] GitHub 推送成功
) else (
echo [FAIL] GitHub 推送失败
)

echo.
echo 当前分支：%CURRENT_BRANCH%
echo Gitee ：%GITEE_URL%
echo GitHub：%GITHUB_URL%
echo.

if not "%GITEE_RESULT%"=="0" (
echo [ERROR] 至少有一个平台推送失败，请检查上方错误。
pause
exit /b 1
)

if not "%GITHUB_RESULT%"=="0" (
echo [ERROR] 至少有一个平台推送失败，请检查上方错误。
pause
exit /b 1
)

echo 全部推送完成。
pause
exit /b 0
