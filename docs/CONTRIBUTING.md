
# Contribution Guide

Thank you for your interest in contributing to our academic open-source project! We value contributions from researchers, developers, students, and anyone who is enthusiastic about advancing knowledge and sharing ideas. This guide outlines how you can contribute effectively.

---

## **Table of Contents**
1. [Code of Conduct](#code-of-conduct)
2. [How to Contribute](#how-to-contribute)
3. [Setting Up the Development Environment](#setting-up-the-development-environment)
4. [Types of Contributions](#types-of-contributions)
5. [Submitting Contributions](#submitting-contributions)
6. [Citation and Acknowledgment](#citation-and-acknowledgment)
7. [Contact Information](#contact-information)

---

## **Code of Conduct**
By contributing to this project, you agree to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). We are committed to fostering an open, respectful, and inclusive community.

---

## **How to Contribute**

### **1. Report Issues**
- Found a bug? Please check if it has already been reported in the [Issues](https://github.com/example/repo/issues) section.
- If not, open a new issue. Provide a clear title and description, and include any relevant details (e.g., code snippets, logs, screenshots).

### **2. Suggest Enhancements**
- Have an idea to improve the project? Open an issue with the "enhancement" label.
- Include a description of the problem and your proposed solution.

### **3. Contribute Code**
- Before starting any work, check the open issues and assigned tasks to avoid duplicate efforts.
- If you're unsure about how to implement a feature, feel free to discuss it in an issue or [discussion forum](https://github.com/example/repo/discussions).

### **4. Documentation**
- Help us improve our documentation by clarifying instructions, adding examples, or fixing errors.

### **5. Share Use Cases**
- If you've used the project in your research or work, share your experience in the [Discussions](https://github.com/example/repo/discussions).

---

## **Setting Up the Development Environment**

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/example/repo.git
   cd repo
   ```

2. **Install Dependencies:**
   Follow the instructions in the `README.md` to install the necessary dependencies.

3. **Run Tests:**
   Ensure the project works correctly on your system:
   ```bash
   ./run_tests.sh
   ```

4. **Create a Branch:**
   For new features or bug fixes, create a new branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```

5. **Code Standards:**
   - Follow the coding style outlined in the `CONTRIBUTING.md` or `STYLE_GUIDE.md`.
   - Use descriptive commit messages and reference related issues (e.g., `Fixes #123`).

---

## **Types of Contributions**

### **1. Bug Fixes**
- Locate the issue in the codebase.
- Write a fix and include a test case that verifies the issue is resolved.

### **2. New Features**
- Propose your idea in an issue first.
- Ensure the feature aligns with the goals of the project.
- Add documentation and tests for your feature.

### **3. Academic Use Cases**
- If the project is used in a paper, dataset, or experiment, add your use case to the `SHOWCASE.md` or relevant documentation.

### **4. Testing**
- Write unit tests, integration tests, or stress tests.
- Use the existing test framework and add tests to the appropriate directory.

### **5. Documentation**
- Update or add documentation to the `docs/` folder or directly in the `README.md`.

---

## **Submitting Contributions**

### **1. Create a Pull Request (PR)**
- Once your work is ready, push it to your branch:
  ```bash
  git push origin feature/your-feature-name
  ```
- Open a pull request on GitHub and provide a detailed description of your changes.

### **2. PR Requirements**
- Pass all automated tests.
- Include documentation for new features or changes.
- Include references to related issues (e.g., "Fixes #123").
- Respond to feedback from maintainers.

---

## **Citation and Acknowledgment**

If this project is used in your research, please cite it using the following format:

```text
@misc{project_name,
  author = {Author Name(s)},
  title = {Project Title},
  year = {Year},
  publisher = {GitHub},
  journal = {GitHub repository},
  howpublished = {\url{https://github.com/example/repo}}
}
```

Add your published paper to the [Showcase](SHOWCASE.md) section to help others learn from your work!

---

## **Contact Information**
If you have questions or need support:
- Open an issue with the "question" label.
- Email the temporary maintainers at `info@screenlake`.

---

We’re excited to see your contributions and appreciate your effort in improving this project!
