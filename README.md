Confluence Markdown Macro
========================

This macro uses the Flexmark library to convert from Markdown to HTML within Confluence.

It can be accessed via:

*   Macro Browser
*   {markdown} tags
*   SOAP API using <ac:macro ac:name="markdown"></ac:macro>

This macro supports the following languages:

*   English
*   French
*   German

This macro DOES NOT support inline HTML. This functionality was removed due to it being perceived as a security vulnerability.


# Release workflow

Create a new release based on the correct release branch and correct tag name.

- release/1.6.x will target Confluence < 9. Eg create a release 1.6.30 with tag 1.6.30 on release/1.6.x
- release/2.x will target Confluence 9, and should be tagged with 2.x.y. Eg create a release 2.0.0 with tag 2.0.0 on release/2.x
- main will target Confluence 10, and should be tagged with 3.x.y. Eg create a release 3.0.0 with tag 3.0.0 on main

