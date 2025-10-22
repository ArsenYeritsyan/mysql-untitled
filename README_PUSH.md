Push to a New Remote

Prerequisites
- Ensure you have a remote repository URL (SSH or HTTPS)
- Authentication set up:
  - SSH: configured SSH key with Git host (e.g., GitHub/GitLab)
  - HTTPS: Personal Access Token (PAT) if the host requires it

One-command push
From the project root, run one of the following:

SSH example (recommended):
  ./push.sh git@github.com:<your-user>/<your-repo>.git main

HTTPS example:
  ./push.sh https://github.com/<your-user>/<your-repo>.git main

Notes
- The script will:
  1) Commit any uncommitted changes (if any)
  2) Rename the current branch to the provided name (default: main)
  3) Configure origin to the provided remote URL
  4) Push with upstream tracking
- If you prefer to keep the branch name as master, replace the last argument with master.

Verification
After pushing, you can verify with:
  git remote -v
  git status -sb
  git branch -vv

Troubleshooting
- Auth failed (HTTPS): ensure you use your PAT as the password.
- Permission denied (SSH): add your SSH public key to the Git hosting service and ensure ssh-agent is running.
- Non-empty remote: if the remote has existing history, you may need to resolve history differences or push with a different branch name.
