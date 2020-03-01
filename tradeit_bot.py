import logging
import re

from selenium.webdriver import Firefox
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


class TradeitBot:
    URL = 'https://tradeit.gg/'
    ITEMS_XPATH = '//div[@id="sinv-loader"]//li'

    def __init__(self, driver: Firefox):
        self.wait_long = WebDriverWait(driver, 30)
        self.wait_short = WebDriverWait(driver, 2)
        self.__driver = driver
        self.__driver.get(self.URL)
        self.wait_long.until(EC.presence_of_element_located((By.ID, 'trading-steps')))

    def search(self, item_name: str, game: str):
        game_options = self.__driver.find_element_by_xpath(f'//div[@id="bot-inv-column"]//li[@game="{game}"]')
        if 'sactive-game' not in game_options.get_attribute('class'):
            self.wait_long.until(
                EC.element_to_be_clickable((By.XPATH, f'//div[@id="bot-inv-column"]//li[@game="{game}"]'))).click()
            self.__wait_for_items()

        search_input = self.__driver.find_element_by_id('ssearch')
        if search_input.text != item_name:
            bot_items = self.__driver.find_elements_by_xpath(self.ITEMS_XPATH)
            last_item_html = bot_items[-1].get_attribute('innerHTML') if bot_items else ''
            search_input.send_keys(item_name)
            self.__wait_for_refreshed_items(last_item_html)

        updated = False
        bot_items = self.__driver.find_elements_by_xpath(self.ITEMS_XPATH)
        last_item_html = bot_items[-1].get_attribute('innerHTML') if bot_items else ''
        for bot_item in bot_items:
            try:
                multiplier = bot_item.find_element_by_xpath('.//div[@class="multiplier"]')
                self.wait_short.until(
                    lambda _: multiplier if multiplier.is_displayed() and multiplier.is_enabled() else False).click()
                updated = True
            except NoSuchElementException:
                pass

        self.__wait_for_refreshed_items(last_item_html) if updated else None

        items = map(self.__web_element_to_dict, self.__driver.find_elements_by_xpath(self.ITEMS_XPATH))
        search_input.clear()
        return items

    def __wait_for_items(self):
        def wait_function(web_driver: Firefox):
            text = web_driver.find_element_by_id('sinv-empty')
            elements = web_driver.find_elements_by_xpath(self.ITEMS_XPATH)
            # logging.debug(f"Waiting for items - Message is displayed: {text.is_displayed()} - Items size: {len(elements)}")
            return text.is_displayed() or elements

        self.wait_long.until(wait_function)

    def __wait_for_refreshed_items(self, initial_state: str):
        def wait_function(web_driver: Firefox):
            bot_items = web_driver.find_elements_by_xpath(self.ITEMS_XPATH)
            current_state = bot_items[-1].get_attribute('innerHTML') if bot_items else ''
            equals = current_state == initial_state
            # logging.debug(f"Waiting for updated items - Initial state lenght: {len(initial_state)} - Current state lenght: {len(current_state)}")
            return not equals

        self.wait_short.until(wait_function)

    def __web_element_to_dict(self, element: WebElement) -> dict:
        float_locator = By.XPATH, './/span[contains(text(), "Float")]//parent::div'
        paint_locator = By.XPATH, './/span[contains(text(), "Paint")]//parent::div'
        price_locator = By.XPATH, './/span[@class="pricetext"]'
        wear_locator = By.XPATH, './/div[@class="quality"]'
        stattrack_locator = By.XPATH, './/div[@class="stattrak"]'
        locked_locator = By.XPATH, './/div[@class="bot-icon"]'

        return {
            'name': str(element.get_attribute('data-original-title')).strip(),
            'game': str(element.get_attribute('data-appid')).strip(),
            'floatvalue': self.__value_of(self.__get_text(element, float_locator)),
            'paint': self.__value_of(self.__get_text(element, paint_locator)),
            'price': self.__value_of(self.__get_text(element, price_locator)),
            'wear': self.__get_text(element, wear_locator),
            'stattrack': True if self.__get_text(element, stattrack_locator) else False,
            'locked': self.__get_text(element, locked_locator)
        }

    @staticmethod
    def __get_text(element: WebElement, locator: tuple):
        try:
            parent = element.find_element(*locator)
            text = str(parent.get_attribute('textContent')).strip()
            children = parent.find_elements(By.XPATH, './*')
            for child in children:
                text = text.replace(child.get_attribute('textContent'), '', 1).strip()
            return text
        except NoSuchElementException:
            return None

    @staticmethod
    def __value_of(text: str):
        text = re.sub(r'[^\d.]', '', text) if text else ''
        try:
            value = float(text)
        except ValueError:
            return None
        else:
            return int(value) if value.is_integer() else value


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)

    driver = Firefox(executable_path='')
    bot = TradeitBot(driver)
    items = bot.search('AK-47 | Elite Build', '730')

    for item in items:
        print(item)
