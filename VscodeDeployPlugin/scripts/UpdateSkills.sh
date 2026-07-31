#!/usr/bin/env bash

# 模拟更新脚本：只打印调试日志，不修改本机 skill 文件。
set -u

echo '[UpdateSkills][debug] 开始执行模拟 skill 更新'
echo "[UpdateSkills][debug] 工作目录: $PWD"
echo "[UpdateSkills][debug] 原始参数数量: $#"

scope=''
global_skills=()
project_skills=()

for arg in "$@"; do
  case "$arg" in
    --global)
      scope='global'
      ;;
    --project)
      scope='project'
      ;;
    *)
      if [ "$scope" = 'global' ]; then
        global_skills+=("$arg")
      elif [ "$scope" = 'project' ]; then
        project_skills+=("$arg")
      else
        echo "[UpdateSkills][debug] 忽略未指定范围的参数: $arg"
      fi
      ;;
  esac
done

echo "[UpdateSkills][debug] 全局 skills (${#global_skills[@]}): ${global_skills[*]:-无}"
echo "[UpdateSkills][debug] 项目级 skills (${#project_skills[@]}): ${project_skills[*]:-无}"

for skill in "${global_skills[@]}"; do
  echo "[UpdateSkills][debug] 模拟更新全局 skill: $skill"
done
for skill in "${project_skills[@]}"; do
  echo "[UpdateSkills][debug] 模拟更新项目级 skill: $skill"
done

echo '[UpdateSkills][debug] 模拟更新完成（未修改任何文件）'
