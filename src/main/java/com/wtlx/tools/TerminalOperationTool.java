package com.wtlx.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 终端操作工具
 */
@Slf4j
public class TerminalOperationTool {

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        log.info("开始执行终端命令: {}", command);

        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("命令执行完成，退出码: {}", exitCode);

            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
                log.warn("命令执行失败，退出码: {}", exitCode);
            } else {
                log.info("命令执行成功");
            }
        } catch (IOException | InterruptedException e) {
            log.error("命令执行异常: {}", e.getMessage(), e);
            output.append("Error executing command: ").append(e.getMessage());
        }

        log.debug("命令输出:\n{}", output.toString());
        return output.toString();
    }
}
