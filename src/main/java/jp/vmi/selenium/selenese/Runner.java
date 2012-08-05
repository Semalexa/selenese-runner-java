package jp.vmi.selenium.selenese;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverCommandProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.vmi.selenium.selenese.command.Command;
import jp.vmi.selenium.selenese.command.Command.Failure;
import jp.vmi.selenium.selenese.command.Command.Result;
import jp.vmi.selenium.utils.LoggerUtils;

import static jp.vmi.selenium.selenese.command.Command.*;

public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    private WebDriver driver;
    private File screenshotDir = null;
    private boolean isScreenshotAll = false;
    private String baseURI = "";

    private void takeScreenshot(int index) {
        FastDateFormat format = FastDateFormat.getInstance("yyyyMMddHHmmssSSS");
        if (!(driver instanceof TakesScreenshot)) {
            log.warn("webdriver is not support taking screenshot.");
            return;
        }
        TakesScreenshot taker = (TakesScreenshot) driver;
        File tmp = taker.getScreenshotAs(OutputType.FILE);
        String dateTime = format.format(Calendar.getInstance().getTime());
        File target = new File(screenshotDir, "capture_" + dateTime + "_" + index + ".png");
        if (!tmp.renameTo(target.getAbsoluteFile()))
            log.error("fail to rename file to :" + target.getAbsolutePath());
        log.info(" - capture screenshot:{}", target.getAbsolutePath());
    }

    public Runner() {
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public File getScreenshotDir() {
        return screenshotDir;
    }

    public void setScreenshotDir(File screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    public boolean isScreenshotAll() {
        return isScreenshotAll;
    }

    public void setScreenshotAll(boolean isScreenshotAll) {
        this.isScreenshotAll = isScreenshotAll;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    public Result evaluate(Context context, Command current) {
        Result totalResult = SUCCESS;
        while (current != null) {
            log.info(current.toString());
            Result result = doCommand(context, current);
            if (isScreenshotAll) {
                takeScreenshot(current.getIndex());
            }
            totalResult = totalResult.update(result);
            if (totalResult.isInterrupted())
                break;
            current = current.next(context);
        }
        return totalResult;
    }

    @DoCommand
    protected Result doCommand(Context context, Command current) {
        Result result = current.doCommand(context);
        return result;
    }

    public Result run(File file) {
        long stime = System.nanoTime();
        String name = file.getName();
        try {
            log.info("Start: {}", name);
            Parser parser = Parser.getParser(file);
            if (parser instanceof TestSuiteParser) {
                Result totalResult = SUCCESS;
                for (File tcFile : ((TestSuiteParser) parser).getTestCaseFiles())
                    totalResult = totalResult.update(run(tcFile));
                return totalResult;
            } else { // if parser instanceof TestCaseParser
                TestCaseParser tcParser = (TestCaseParser) parser;
                log.info("Testcase: {}", tcParser.getName());
                String baseURI = StringUtils.isBlank(this.baseURI) ? tcParser.getBaseURI() : this.baseURI;
                log.info("baseURI: {}", baseURI);
                WebDriverCommandProcessor proc = new WebDriverCommandProcessor(baseURI, driver);
                Context context = new Context(proc, driver);
                return evaluate(context, tcParser.parse(proc, context));
            }
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            throw e;
        } catch (InvalidSeleneseException e) {
            log.error(e.getMessage());
            return new Failure(e.getMessage());
        } finally {
            log.info("End({}): {}", LoggerUtils.durationToString(stime, System.nanoTime()), name);
        }
    }

    public Result run(List<File> files) {
        Result totalResult = SUCCESS;
        for (File file : files)
            totalResult = totalResult.update(run(file));
        return totalResult;
    }
}
