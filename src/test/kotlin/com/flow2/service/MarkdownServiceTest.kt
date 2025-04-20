package com.flow2.service

import com.flow2.repository.assets.FSSiteAssetRepository
import com.flow2.repository.media.FSMediaRepository
import kotlin.test.Test

class MarkdownServiceTest {

    private val markdownService = MarkdownService(
        siteAssetRepository = FSSiteAssetRepository("src/main/resources/assets"),
        mediaRepository = FSMediaRepository("src/main/resources/media"),
    )

    private val mediaDir = "/media/post-id-123"

    private val mdContent = """
---
title: The Master & Margarita
publishedAt: 
tags: fiction, Russia, Soviet Union, magical realism, Satan, love
category: BOOK_REVIEW
---

> Follow me, reader! Who told you that there is no true, eternal, and faithful love in the world! May the liar have his foul tongue cut out! Follow me, my reader, and only me, and I will show you such a love!
> 
> — Mikhail Bulgakov, *The Master and Margarita* pg. 180

<style>
input[type="number"] {
width: 100px;
margin-bottom: 10px;
}
label {
display: inline-block;
width: 300px;
text-align: left;
margin-right: 10px;
}
.result {
font-weight: bold;
}
.container {
border: 1px solid #ccc;
padding: 10px;
margin-bottom: 20px;
}
</style>
<div class="container">
<h1>Bitcoin Mining Calculator</h1>
<div>
<label for="yourHashrate">Your Hashrate (TH/s):</label>
<input type="number" id="yourHashrate" step="0.1" value="1"><br>
<label for="globalHashrate">Global Hashrate (EH/s):</label>
<input type="number" id="globalHashrate" step="0.1" value="1000"><br>
<label for="energyConsumption">Energy Consumption (kW):</label>
<input type="number" id="energyConsumption" step="0.001" value="0.017"><br>
<label for="costOfEnergy">Cost of Energy ($/kWh):</label>
<input type="number" id="costOfEnergy" step="0.001" value="0.192"><br>
<label for="hardwareCost">Cost of Mining Hardware ($):</label>
<input type="number" id="hardwareCost" step="0.01" value="220"><br>
<label for="blockReward">Mining Block Reward (BTC):</label>
<input type="number" id="blockReward" step="0.01" value="3.125"><br>
<label for="bitcoinPrice">Price of Bitcoin ($):</label>
<input type="number" id="bitcoinPrice" step="0.01" value="120000"><br>
<label for="miningYears">Length of Mining (years):</label>
<input type="number" id="miningYears" step="0.1" value="3"><br>
<button onclick="calculateEV()">Calculate</button>
</div>
<h2>Results</h2>
<div>
<p>Monthly Cost: $<span id="monthlyCost" class="result">0.00</span></p>
<p>Monthly EV Profit: $<span id="monthlyProfit" class="result">0.00</span></p>
<p>Monthly Expected Value (EV): $<span id="monthlyEV" class="result">0.00</span></p>
<p>Return on Investment (ROI): <span id="roi" class="result">0.00</span>%</p>
</div>
<script>
function calculateEV() {
const yourHashrate = parseFloat(document.getElementById('yourHashrate').value) || 0;
const globalHashrate = parseFloat(document.getElementById('globalHashrate').value) * 1000000 || 0; // Convert EH/s to TH/s
const energyConsumption = parseFloat(document.getElementById('energyConsumption').value) || 0;
const costOfEnergy = parseFloat(document.getElementById('costOfEnergy').value) || 0;
const hardwareCost = parseFloat(document.getElementById('hardwareCost').value) || 0;
const blockReward = parseFloat(document.getElementById('blockReward').value) || 0;
const bitcoinPrice = parseFloat(document.getElementById('bitcoinPrice').value) || 0;
const miningYears = parseFloat(document.getElementById('miningYears').value) || 0;
const months = miningYears * 12;
const blocksPerMonth = 4320; // Assuming 1 block every 10 minutes
const monthlyCost = (energyConsumption * 24 * 30 * costOfEnergy) + (hardwareCost / months);
const monthlyProfit = ((yourHashrate / globalHashrate) * blockReward * blocksPerMonth * bitcoinPrice);
const monthlyEV = monthlyProfit - monthlyCost;
const totalInvestment = hardwareCost + (monthlyCost * months);
const roi = ((monthlyProfit * months) - totalInvestment) / totalInvestment * 100;
document.getElementById('monthlyCost').innerText = monthlyCost.toFixed(2);
document.getElementById('monthlyProfit').innerText = monthlyProfit.toFixed(2);
document.getElementById('monthlyEV').innerText = monthlyEV.toFixed(2);
document.getElementById('roi').innerText = roi.toFixed(2);
}
calculateEV();
</script>
</div>

One thing I need to start doing before reading a foreign language novel is decide what translation to read. If it's been translated more than once, there will certainly be differences in the versions—in prose, grammar, and closeness to the original text. 
    """.trimIndent()

    @Test
    fun parseHtmlContent() {
        val html = markdownService.parseHtmlContent(mdContent, mediaDir)
        println(html)
        // print to file
        val file = java.io.File("src/test/testoutput.html")
        file.writeText(html)
        println("HTML content written to ${file.absolutePath}")
    }

    @Test
    fun parseFrontMatter() {
        val frontMatter = markdownService.parseFrontMatter(mdContent)
        println(frontMatter["title"]?.firstOrNull())
        println(frontMatter["publishedAt"]?.firstOrNull())
        println(frontMatter["tags"])
        println(frontMatter["category"]?.firstOrNull())
    }


}