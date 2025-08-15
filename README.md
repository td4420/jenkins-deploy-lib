# Jenkins Shared Library

This is a reusable Jenkins Shared Library for common pipeline steps.

## 📌 Structure
- **vars/**: Global steps callable from Jenkinsfiles.
- **src/**: Groovy utility classes.
- **resources/**: Templates and config files.

## 🚀 Usage
1. Configure Jenkins → Manage Jenkins → Configure System → Global Pipeline Libraries.
2. Add this repo with a name, e.g. `my-shared-lib`.
3. Load in your Jenkinsfile:
```groovy
@Library('my-shared-lib') _
sayHello('World')
deployApp(env: 'staging', branch: 'release/1.0')
