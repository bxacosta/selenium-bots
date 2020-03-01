import lombok.extern.apachecommons.CommonsLog;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@CommonsLog
public class TradeitBot {

    public static final String URL = "https://tradeit.gg/";
    private static final By ITEMS_LOCATOR = By.xpath("//div[@id='sinv-loader']//li");

    private final FirefoxDriver driver;
    private final WebDriverWait wait;

    public TradeitBot(FirefoxDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, 30);
        this.driver.get(URL);
        this.wait.until(ExpectedConditions.presenceOfElementLocated(By.id("trading-steps")));
    }

    public List<Map<String, Object>> search(String itemName, String game) {
        var gameOption = driver.findElementByXPath("//div[@id='bot-inv-column']//li[@game='" + game + "']");
        if (!gameOption.getAttribute("class").contains("sactive-game")) {
            wait.until(ExpectedConditions.elementToBeClickable(gameOption)).click();
            waitForItems();
        }

        var searchInput = driver.findElementById("ssearch");
        if (!searchInput.getText().equals(itemName)) {
            var botItems = driver.findElements(ITEMS_LOCATOR);
            var lastItemHTML = !botItems.isEmpty() ? botItems.get(botItems.size() - 1).getAttribute("innerHTML") : "";
            searchInput.sendKeys(itemName);
            waitForRefreshedItems(lastItemHTML);
        }

        var updated = false;
        var botItems = driver.findElements(ITEMS_LOCATOR);
        var lastItemHTML = !botItems.isEmpty() ? botItems.get(botItems.size() - 1).getAttribute("innerHTML") : "";
        for (var botItem : botItems) {
            try {
                var multiplier = botItem.findElement(By.xpath(".//div[@class='multiplier']"));
                wait.until(ExpectedConditions.elementToBeClickable(multiplier)).click();
                updated = true;
            } catch (NoSuchElementException ignored) {
            }
        }
        if (updated) {
            waitForRefreshedItems(lastItemHTML);
        }

        var items = driver.findElements(ITEMS_LOCATOR).stream()
                .map(this::convertWebElementToItem)
                .collect(Collectors.toList());

        searchInput.clear();
        return items;
    }

    private void waitForItems() {
        wait.until(webDriver -> {
            var text = webDriver.findElement(By.id("sinv-empty"));
            var elements = webDriver.findElements(ITEMS_LOCATOR);
            log.info("Waiting for items - Message is displayed: " + text.isDisplayed() + " - Items size: " + elements.size());
            return text.isDisplayed() || !elements.isEmpty();
        });
    }

    private void waitForRefreshedItems(String initialState) {
        wait.withTimeout(Duration.ofSeconds(2)).until(webDriver -> {
            var botItems = driver.findElements(ITEMS_LOCATOR);
            var currentState = !botItems.isEmpty() ? botItems.get(botItems.size() - 1).getAttribute("innerHTML") : "";
            var equals = currentState.equals(initialState);
            log.info("Waiting for updated items - Initial state lenght: " + initialState.length() + " - Current state lenght: " + currentState.length());
            return !equals;
        });
    }

    private Map<String, Object> convertWebElementToItem(WebElement element) {
        var floatLocator = By.xpath(".//span[contains(text(), 'Float')]//parent::div");
        var paintLocator = By.xpath(".//span[contains(text(), 'Paint')]//parent::div");
        var priceLocator = By.xpath(".//span[@class='pricetext']");
        var wearLocator = By.xpath(".//div[@class='quality']");
        var stattrakLocator = By.xpath(".//div[@class='stattrak']");
        var lockedLocator = By.xpath(".//div[@class='bot-icon']");

        var item = new HashMap<String, Object>();
        item.put("name", element.getAttribute("data-original-title"));
        item.put("game", element.getAttribute("data-appid"));
        item.put("floatvalue", valueOf(getText(element, floatLocator), Double::valueOf));
        item.put("paint", valueOf(getText(element, paintLocator), Integer::valueOf));
        item.put("price", valueOf(getText(element, priceLocator), Double::valueOf));
        item.put("wear", getText(element, wearLocator));
        item.put("stattrack", getText(element, stattrakLocator) != null);
        item.put("locked", getText(element, lockedLocator));
        return item;
    }

    private String getText(WebElement element, By locator) {
        try {
            var parent = element.findElement(locator);
            var text = parent.getAttribute("textContent").trim();
            var children = parent.findElements(By.xpath("./*"));
            for (var child : children) {
                text = text.replaceFirst(child.getAttribute("textContent"), "").trim();
            }
            return text;
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private <T> T valueOf(String text, Function<String, T> valueOfFunction) {
        if (text == null) return null;
        text = text.replaceAll("[^\\d.]", "");
        try {
            return valueOfFunction.apply(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
