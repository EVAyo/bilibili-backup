@echo off
color 3F
title=���������˺Ż�ԭ

if "%1" equ "" (
start /max call %0 1
exit
)

echo �Ƿ�ԭ��
echo �����������
pause >nul 2>&1

set bin=%cd%\bin

"%bin%\jre\bin\java" -jar "%bin%\bilibili-backup.jar" restore

echo ��������رմ���
pause>nul
exit
