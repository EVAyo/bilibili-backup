@echo off
color 3F
title=��������һ���Ѷ�������Ϣ

if "%1" equ "" (
start /max call %0 1
exit
)

echo �Ƿ�һ���Ѷ�������Ϣ��
echo �����������
pause >nul 2>&1

set bin=%cd%\bin

"%bin%\jre\bin\java" -jar "%bin%\bilibili-backup.jar" read_all_msg

echo ��������رմ���
pause>nul
exit
