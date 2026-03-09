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

/**Stats dashboard pases each stat to this component, this handles the parsing and rendering */
export class StatDisplayComponent implements OnInit, AfterViewInit {

  @Input("stat") stat; //object {outputLabel, outputType, results} diferent outputType can have diferent props
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
      console.log(this.stat)
    }
  }

  

  ngAfterViewInit(): void {
    if(this.stat.outputType=="numberGraph"){
      let results = [];
      results = JSON.parse(this.stat.results);
      let resultsToChar = results.map( result => {
        return {x:result[0], y:result[1]}});
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
