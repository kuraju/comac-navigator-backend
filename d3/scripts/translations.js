/**
 * @fileOverview Translations object.
 *
 * @author Michał Oniszczuk <m.oniszczuk@icm.edu.pl>
 */

const LANG_PL = "pl";
const LANG_EN = "en";

const TRANS_ATTRIBUTE = "data-i18n";

/**
 * Translations.
 *
 * @namespace
 */
translations = {
    selectedLanguage: LANG_EN,

    /**
     * Toggles the currently selected language between LANG_PL and LANG_EN.
     */
    toggleLanguage: function () {
        if (this.selectedLanguage == LANG_PL) {
            this.changeLanguage(LANG_EN);
        } else {
            this.changeLanguage(LANG_PL);
        }
    },

    /**
     * Change the currently selected language and update all elements in the
     * interface that have the TRANS_ATTRIBUTE attribute.
     * @param {string} toLang change to this language
     */
    changeLanguage: function (toLang) {
        this.selectedLanguage = toLang;
        this.translateAll();
    },

    /**
     * Translate all elements in the interface that have the attribute
     * TRANS_ATTRIBUTE.
     * @private
     */
    translateAll: function () {
        $("[" + TRANS_ATTRIBUTE + "]")
            .each(function () {
                translations.translate($(this));
            });
    },

    /**
     * Translate the text inside the provided interface element using the
     * TRANS_ATTRIBUTE attribute value as a key.
     * @param {jQuery} elem
     */
    translate: function (elem) {
        var translationKey = elem.attr(TRANS_ATTRIBUTE);
        var newText = translations.getText(translationKey);
        elem.text(newText);
    },

    /**
     * Get the translated string.
     * @param {string} key get string for this key
     * @returns {string} translated string
     */
    getText: function (key) {
        return this.texts[key][this.selectedLanguage];
    },

    texts: {
        languageSelector: {
            en: "Zmień język interfejsu na polski.",
            pl: "Change interface language to english."
        },
        search: {
            en: "Search",
            pl: "Szukaj"
        },
        searching: {
            en: "Searching...",
            pl: "Szukam..."
        },
        searchError: {
            en: "Unable to load data, server error.",
            pl: "Błąd serwera, nie można było załadować danych."
        },
        hasMoreResults: {
            en: "There are more results. Try to narrow query.",
            pl: "Jest więcej wyników. Spróbuj zawęzić zapytanie."
        },
        noMoreResults: {
            en: "No more results.",
            pl: "Nie ma więcej wyników."
        },
        sidebarHelp: {
            en: "To add the desired result to the graph, " +
            "please drag and drop it on a canvas on the right.",
            pl: "Aby dodać żądany wynik do grafu, " +
            "przeciągnij go na obszar pracy po prawej."
        },
    },
};


