import { AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { Chart } from 'chart.js/auto';
import { Colors } from 'chart.js';
import { getRandomPalete } from '../colors';

Chart.register(Colors);

@Component({
  selector: 'app-stat-display',
  templateUrl: './stat-display.component.html',
  styleUrls: ['./stat-display.component.scss']
})

/**Stats dashboard passes each stat to this component, this handles the parsing and rendering */
export class StatDisplayComponent implements OnInit, AfterViewInit {

  @Input("stat") stat; //object {outputLabel, outputType, results} different outputType can have different props
  defaultIcon="dashboard";
  defaultIconColor="#212121";
  defaultBgColor="#fff";
  defaultTextColor = "#000000";
  options = {
    plugins: {
      colors: {
        enabled:false,
        forceOverride:true
      },
      legend:{display:false},
      title: {
        display: true,
        text: ''
    }
    },
    responsive:true,
    maintainAspectRatio:true,
    scales:{
      y:{
        grid:{
          borderColor:"green",
          borderWith:1,
          drawOnChartArea:false,
          drawBorder:false
        }
      },
      x:{
        grid:{
          borderColor:"green",
          borderWith:1,
          drawOnChartArea:false,
          drawBorder:false
        }
      }
    }
  };


  @ViewChild('chartCanvas') chartCanvas:ElementRef<HTMLCanvasElement>;
  constructor() { }

  ngOnInit(): void {
    if(this.stat.outputType=="size"){ //results is an object: {size=12mb, numOfFounds=120}
      this.stat.results=JSON.parse(this.stat.results.replace(/=/g, ":"));
    }
  }



  ngAfterViewInit(): void {
    this.initGraph();
  }

  initGraph(){
    if(this.stat.outputType=="numberGraph"){
      this.getBarChartGraph();
    }else if(this.stat.outputType=="doughnutChart"){
      this.getDoughnutChart();
    }
    else if(this.stat.outputType=="timeGraph"){//time graph is [ {dataset1}, {dataSet2} ]
      this.getLineChart()
    }
  }

  getBarChartGraph(){
    let results = [];
    results = JSON.parse(this.stat.results);
    let resultsToChar = results.map( result => { return {x:result[0], y:result[1]} });
    this.options.plugins.title.text = this.stat.outputLabel
    let chart = new Chart(this.chartCanvas.nativeElement,{
      type:'bar',
      data: {
        datasets:[{
          data:resultsToChar,
          backgroundColor: getRandomPalete(),
        }]
      },
      options:this.options
    })
  }

  getDoughnutChart(){
    let results = [];
    results = JSON.parse(this.stat.results);
    let resultsLabels = results.map(result => result[0]);
    let resultsData = results.map(result =>  result[1]);
    this.options.plugins.title.text = this.stat.outputLabel
    this.options.scales = null;
    this.options.plugins.legend.display = true
    let chart = new Chart(this.chartCanvas.nativeElement,{
      type:'doughnut',
      data: {
        datasets:[{
          data:resultsData,
          backgroundColor: getRandomPalete(),
        }],
        labels: resultsLabels
      },
      options:this.options
    })
  }

  getLineChart(){
    let results =  [];
      results = JSON.parse(this.stat.results);
      let labels = Object.keys(results[0])//use param order to extract array of labels and sort
      labels = labels.sort( (labelA, labelB) =>  results[0][labelA].order > results[0][labelB].order ? 1 : -1 );
      let dataset = []; //[ {label, data} , {label, data} ];
      let colors = getRandomPalete();
      let indexColor = 0;
      results.forEach( (result, index) => {
        indexColor ++;
        indexColor = indexColor > colors.length ? 0 : indexColor; //restart if array is oversized
        const resultLabel = result[labels[0]].label;
        const resultData = [];
        labels.forEach(label => {resultData.push(result[label].count )});
        dataset.push( {label:resultLabel, data:resultData, borderColor: colors[indexColor], backgroundColor:colors[indexColor] } );
      })
      let data = {
        labels:labels,
        datasets: dataset
      };
      let option = {...this.options};
      option.plugins.legend.display = true
      let chart = new Chart(this.chartCanvas.nativeElement,{
        type:'line',
        data: data,
        options:option
      })
  }

  getbgcolor(){
    if(this.stat.hasOwnProperty("outputCardbgColor") && this.stat.outputCardbgColor != ""){
      return this.stat.outputCardbgColor;
    }
    return this.defaultBgColor;
  }

  getTextColor(){
    if(this.stat.hasOwnProperty("outputTextColor") && this.stat.outputTextColor != ""){
      return this.stat.outputTextColor;
    }
    return this.defaultTextColor;
  }

  getIcon(){
    if(this.stat.hasOwnProperty("outputIcon") && this.stat.outputIcon != ""){
      return this.stat.outputIcon;
    }
    return this.defaultIcon;
  }

  getIconColor(){
    if(this.stat.hasOwnProperty("outputIconColor") && this.stat.outputIconColor != ""){
      return this.stat.outputIconColor;
    }
    return this.defaultIconColor;
  }
}
