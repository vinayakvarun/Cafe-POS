#!/bin/bash
sed -i '/NavigationBarItem(/N;s/NavigationBarItem(\n *NavigationBarItem(/NavigationBarItem(/g' app/src/main/java/com/example/ui/AppNavigation.kt
