package mimik.networkRouting.routers

import io.ktor.*
import io.ktor.routing.*
import kotlinx.css.*
import kotlinx.html.*
import java.util.*

enum class ExportStyles {
    Common, Breadcrumb, Collapsible_div,
    Tooltip, Callout_div;

    override fun toString(): String = "${name.toLowerCase(Locale.ROOT)}.css"

    val asset: String get() = "../assets/css/$this"
}

interface ExportStyle {
    val exportName: ExportStyles
    val data: String

    /**
     * Exposes the [data] to [filename] as a `Text/CSS`
     */
    fun expose(route: Route) = route.cssData(exportName.toString()) { data }
}

fun exposeDeclaredStyles(route: Route) {
    route.apply {
        CommonStyles.expose(this)
        BreadcrumbStyle.expose(this)
        CollapsibleDiv.expose(this)
        TooltipStyle.expose(this)
        CalloutStyle.expose(this)
    }
}

object CommonStyles : ExportStyle {
    override val exportName: ExportStyles
        get() = ExportStyles.Common

    override val data: String
        get() = """
            table {
                    font: 1em Arial;
                    border: 1px solid black;
                    width: 100%;
                }
                
                button {
                    cursor: pointer;
                }
                
                button:disabled {
                    cursor: default;
                }
                
                .inputButton {
                    border: 1px solid #ccc;
                    border-radius: 4px;
                }

                th {
                    background-color: #ccc;
                    width: auto;
                }
                td {
                    background-color: #eee;
                }
                th, td {
                    text-align: left;
                    padding: 0.4em 0.4em;
                }

                .btn_50wide {
                    width: 50%
                }
                .tb_25wide {
                    width: 25%
                }
                .center{ text-align: center; }
                .infoText {
                    font-size: 14px;
                    color: #555
                }
                
                .opacity50 { opacity: 0.5; }
                
                .radioDiv {
                    width: 40%;
                    padding: 6px;
                }
                
                .hoverExpand:hover {
                    width: -webkit-fill-available;
                }
        """.trimIndent()
}

object BreadcrumbStyle : ExportStyle {
    override val exportName: ExportStyles
        get() = ExportStyles.Breadcrumb

    override val data: String
        get() = """
        .breadcrumb {
            padding: 10px;
            position: sticky;
            top: 10px;
            width: calc(100% - 22px);
            background-color: #eee;
            overflow: hidden;
            border: 1px solid black;
            border-radius: 5px;
            z-index: 1;
        }
        
        .breadcrumb div {
            font-size: 18px;
        }
        
        .breadcrumb .subnav+.subnav:before {
            content: "/";
        }
        
        .subnav {
            float: left;
            overflow: hidden;
        }
        
        .caret-down:after {
            content: "\25be";
            line-height: 1;
            font-style: normal;
            text-rendering: auto;
        }
        
        .navHeader {
            color: #0275d8;
            text-decoration: none;
        }
        
        .navHeader:hover {
            color: white;
            cursor: pointer;
            text-decoration: underline;
        }
        
        .subnav .navHeader {
            font-size: 16px;  
            border: none;
            outline: none;
            background-color: inherit;
            font-family: inherit;
            margin: 0;
            padding: 4px;
        }
        
        .navHeader:hover, .subnav-content *:hover {
            background-color: darkslategray;
        }
        
        .subnav-content {
            position: fixed;
            left: 5em;
            top: 42px;
            width: auto;
            background-color: slategray;
            z-index: 1;
            line-height: 1em;
            max-height: 10.5em;
            overflow-y: auto;
            background-color: transparent;
            border-top: 12px solid transparent;
            display: none;
        }
        
        .subnav-content * {
            cursor: pointer;
            float: left;
            color: white;
            padding: 8px;
            padding-right: 10em;
            text-decoration: none;
            display: inline-flex;
            background-color: slategrey;
            border-bottom: 1px solid;
        }
        
        .subnav:hover .subnav-content {
            display: grid;
        }
        """.trimIndent()

    // https://ktor.io/docs/css-dsl.html#use_css
    val g_BreadcrumbStyle: String
        get() {
//            val aa = CSSBuilder {
//                classRule("breadcrumb") {
//                    padding = 10.px.toString()
//                    position = Position.sticky
//                    top = 10.px
//                    width = 100.pct - 22.px
//                    backgroundColor = Color("#eee")
//                    overflow = Overflow.hidden
//                    border = "1px solid black"
//                    borderRadius = 5.px
//                    zIndex = 1
//
//                    div {
//                        fontSize = 10.px
//                    }
//
//                    classDescendant("subnav") {
//                        this.parent
//                        float = Float.left
//                        overflow = Overflow.hidden
//
//                        classAdjacentSibling("subnav:before") {
//                            content = "/".quoted
//                        }
//                    }
//                }
//
//                classRule("caret-down") {
//                    after {
//                        content = "\\25be".quoted
//                        lineHeight = 1.px.lh
//                        fontStyle = FontStyle.normal
//                    }
//                }
//            }.toString(true)

            return ""
        }
}

object CollapsibleDiv : ExportStyle {
    override val exportName: ExportStyles
        get() = ExportStyles.Collapsible_div

    override val data: String
        get() = """
            /* Button style that is used to open and close the collapsible content */
            .collapsible {
                background-color: #999;
                color: white;
                cursor: pointer;
                padding: 8px 10px;
                margin-bottom: 4px;
                width: 100%;
                text-align: left;
                font-size: 15px;
            }
                
            .collapsible:after {
                content: '\002B';
                color: white;
                font-weight: bold;
                float: right;
                margin-left: 5px;
            }
            
            /* Background color to the button if it is clicked on (add the .active class with JS), and when you move the mouse over it (hover) */
            .active, .collapsible:hover {
                background-color: #888;
            }
            .active:after {
                content: "\2212";
            }
            
            /* Style the collapsible content. Note: "hidden" by default */
            .hideableContent {
                padding: 6px;
                max-height: 0;
                display: none;
                overflow: hidden;
                width: -webkit-fill-available;
                background-color: #f4f4f4;
                transition: max-height 0.4s ease-out;
            }
        """.trimIndent()
}

object TooltipStyle : ExportStyle {
    override val exportName: ExportStyles
        get() = ExportStyles.Tooltip

    override val data: String
        get() = """
            .tooltip {
                position: relative;
                display: inline-block;
                border-bottom: 1px dotted #ccc;
                color: #006080;
                cursor: default;
            }
            
            .tooltip .tooltiptext {
                visibility: hidden;
                position: absolute;
                width: 30em;
                max-width: max-content;
                background-color: #555;
                color: #fff;
                text-align: center;
                padding: 0.5em;
                border-radius: 6px;
                z-index: 1;
                opacity: 0;
                transition: opacity 0.3s;
            }
            
            .tooltip:hover .tooltiptext {
                visibility: visible;
                opacity: 1;
            }
            
            .tooltip-right {
                top: -5px;
                left: 125%;  
            }
            
            .tooltip-right::after {
                content: "";
                position: absolute;
                top: 50%;
                right: 100%;
                margin-top: -5px;
                border-width: 5px;
                border-style: solid;
                border-color: transparent #555 transparent transparent;
            }
            
            .tooltip-bottom {
                top: 135%;
                left: 50%;  
                margin-left: -60px;
            }
            
            .tooltip-bottom::after {
                content: "";
                position: absolute;
                bottom: 100%;
                left: 50%;
                margin-left: -5px;
                border-width: 5px;
                border-style: solid;
                border-color: transparent transparent #555 transparent;
            }
            
            .tooltip-top {
                bottom: 125%;
                left: 50%;  
                margin-left: -60px;
            }
            
            .tooltip-top::after {
                content: "";
                position: absolute;
                top: 100%;
                left: 50%;
                margin-left: -5px;
                border-width: 5px;
                border-style: solid;
                border-color: #555 transparent transparent transparent;
            }
            
            .tooltip-left {
                top: -5px;
                bottom:auto;
                right: 128%;  
            }
            
            .tooltip-left::after {
                content: "";
                position: absolute;
                top: 50%;
                left: 100%;
                margin-top: -5px;
                border-width: 5px;
                border-style: solid;
                border-color: transparent transparent transparent #555;
            }
        """.trimIndent()
}

object CalloutStyle : ExportStyle {
    override val exportName: ExportStyles
        get() = ExportStyles.Callout_div

    override val data: String
        get() = """
            .callout {
                position: fixed;
                max-width: 300px;
                margin-left: 40%;
                margin-right: 40%;
                width: 30%;
                top: 0;
                z-index: 20;
                transition: top 0.2s;
            }
            
            .callout-header {
                padding: 2px 20px;
                padding-right: 30px;
                padding-left: 10px;
                background: #555;
                font-size: 30px;
                color: white;
            }
            
            .callout-container {
                padding: 6px;
                background-color: #ccc;
                color: black
            }
            
            .closebtn {
                position: absolute;
                top: 0px;
                right: 6px;
                color: white;
                font-size: 30px;
                cursor: pointer;
            }
            
            .closebtn:hover {
                color: lightgrey;
            }
        """.trimIndent()
}

object StyleUtils {
    @Deprecated("Migrate to css files")
    fun FlowOrMetaDataContent.setupStyle() {
        unsafeStyle {
            +arrayOf(
                CommonStyles.data,
                BreadcrumbStyle.data,
                CollapsibleDiv.data,
                TooltipStyle.data,
                CalloutStyle.data
            ).joinToString(separator = "\n")
        }
    }
}
