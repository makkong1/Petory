#!/usr/bin/env python3
import sys
import os
import json
import logging
import subprocess
from datetime import datetime

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

PHASES_DIR = 'phases'
INDEX_FILE = os.path.join(PHASES_DIR, 'index.json')

def run_command(command, cwd=None):
    try:
        result = subprocess.run(command, shell=True, check=True, capture_output=True, text=True, cwd=cwd)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        logging.error(f"Command failed: {command}\nError: {e.stderr}")
        return None

def load_json(file_path):
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            return json.load(f)
    return None

def save_json(file_path, data):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    with open(file_path, 'w') as f:
        json.dump(data, f, indent=2)

def initialize_task(task_name):
    index = load_json(INDEX_FILE) or {"phases": []}
    
    # Check if task already exists in index
    task_entry = next((p for p in index["phases"] if p["dir"] == task_name), None)
    if not task_entry:
        index["phases"].append({"dir": task_name, "status": "pending"})
        save_json(INDEX_FILE, index)
    
    task_dir = os.path.join(PHASES_DIR, task_name)
    task_file = os.path.join(task_dir, 'index.json')
    
    if not os.path.exists(task_file):
        # Default skeleton if not present
        logging.warning(f"Task index for '{task_name}' not found. Please create {task_file} with step definitions.")
        return None
    
    return load_json(task_file)

def setup_git_branch(task_name):
    branch_name = f"feat-{task_name}"
    current_branch = run_command("git rev-parse --abbrev-ref HEAD")
    
    if current_branch == branch_name:
        return True
    
    # Check if branch exists
    branches = run_command("git branch --list " + branch_name)
    if branch_name in branches:
        logging.info(f"Switching to existing branch: {branch_name}")
        run_command(f"git checkout {branch_name}")
    else:
        logging.info(f"Creating and switching to new branch: {branch_name}")
        run_command(f"git checkout -b {branch_name}")
    
    return True

def run_step(task_name, task_data):
    steps = task_data.get("steps", [])
    next_step = next((s for s in steps if s["status"] == "pending"), None)
    
    if not next_step:
        logging.info(f"All steps for '{task_name}' are completed.")
        return
    
    step_num = next_step["step"]
    step_name = next_step["name"]
    step_file = os.path.join(PHASES_DIR, task_name, f"step{step_num}.md")
    
    logging.info(f"--- [Step {step_num}: {step_name}] ---")
    next_step["started_at"] = datetime.now().isoformat()
    
    # Context aggregation: previous step summaries
    prev_summaries = [s.get("summary") for s in steps if s["status"] == "completed" and s.get("summary")]
    
    if prev_summaries:
        print("\n[PREVIOUS STEPS CONTEXT]")
        for i, summary in enumerate(prev_summaries):
            print(f"- Step {i}: {summary}")
    
    if os.path.exists(step_file):
        print(f"\n[INSTRUCTIONS from {step_file}]")
        with open(step_file, 'r') as f:
            print(f.read())
    else:
        print(f"\n[WARNING] {step_file} not found. Please follow the task definition in index.json.")

    save_json(os.path.join(PHASES_DIR, task_name, 'index.json'), task_data)

def main():
    if len(sys.argv) < 2:
        logging.error("Usage: python3 scripts/execute.py <task-name>")
        sys.exit(1)
        
    task_name = sys.argv[1]
    
    task_data = initialize_task(task_name)
    if not task_data:
        sys.exit(1)
        
    setup_git_branch(task_name)
    run_step(task_name, task_data)

if __name__ == "__main__":
    main()
